package com.reecegriffin.jpack.JPack;
import org.openide.util.Exceptions;

import com.yahoo.platform.yui.compressor.CssCompressor;

import java.awt.HeadlessException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;

import java.io.*;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesFileCredentialsProvider;

import javax.net.ssl.HttpsURLConnection;

import org.json.*;
public class App {
	
	private static final String CRITICAL_JS = "<script>!function(e){\"use strict\";var t=function(t,n,r){function o(e){if(i.body)return e();setTimeout(function(){o(e)})}function a(){d.addEventListener&&d.removeEventListener(\"load\",a),d.media=r||\"all\"}var l,i=e.document,d=i.createElement(\"link\");if(n)l=n;else{var s=(i.body||i.getElementsByTagName(\"head\")[0]).childNodes;l=s[s.length-1]}var u=i.styleSheets;d.rel=\"stylesheet\",d.href=t,d.media=\"only x\",o(function(){l.parentNode.insertBefore(d,n?l:l.nextSibling)});var f=function(e){for(var t=d.href,n=u.length;n--;)if(u[n].href===t)return e();setTimeout(function(){f(e)})};return d.addEventListener&&d.addEventListener(\"load\",a),d.onloadcssdefined=f,f(a),d};\"undefined\"!=typeof exports?exports.loadCSS=t:e.loadCSS=t}(\"undefined\"!=typeof global?global:this),function(e){if(e.loadCSS){var t=loadCSS.relpreload={};if(t.support=function(){try{return e.document.createElement(\"link\").relList.supports(\"preload\")}catch(e){return!1}},t.poly=function(){for(var t=e.document.getElementsByTagName(\"link\"),n=0;n<t.length;n++){var r=t[n];\"preload\"===r.rel&&\"style\"===r.getAttribute(\"as\")&&(e.loadCSS(r.href,r,r.getAttribute(\"media\")),r.rel=null)}},!t.support()){t.poly();var n=e.setInterval(t.poly,300);e.addEventListener&&e.addEventListener(\"load\",function(){t.poly(),e.clearInterval(n)}),e.attachEvent&&e.attachEvent(\"onload\",function(){e.clearInterval(n)})}}}(this);</script>";
	
	/**
	 * Packs a website using a server side scripting language for production deployment; looks for all server side scripts files in docRoot that are listed in the config.json file, 
	 * creates critical inline html files for them all, takes the critical code out of the html; re-injects it into the server side scripting files
	 * uploads them to the prod server.  Minify's javascript, css, xml, json using the Google's Closure compiler, YUIcompressor, and other tools.
	 * Uploads js & css to s3 and invalidates cloudfront cache.  Changing the path to dev vs prod (un-minified vs minified) js/css is not the responsibility of this 
	 * code, rather the server side scripting language page should figure out which version of the page to show based off of whether the code is running in dev or prod.
	 * 
	 * Requirements: 
	 * config.json to be in the same classpath as JPack 
	 * node and critical CLI have to be installed on the same machine that runs this code - https://github.com/addyosmani/critical
	 * @param args - One String argument is accepted, the path to config.json if no argument is given config.json will be loaded from the classpath of ./config.json
	 */
	public static void main(String[] args) throws Exception{
		//TODO: This code needs to be tested for a windows local environment
		//TODO: Add last modified checking code so as only to re-pack files that have changed since this was last run
		//TODO: Consider integrating with imagemagik in order to optimize images too
		//TODO: Runtime warning org.openide.util.NbPreferences getPreferencesProvider: NetBeans implementation of Preferences not found is given.  Should strip code of all Net Beans dependencies where possible 
		JSONObject config = new JSONObject(new String(Files.readAllBytes(Paths.get((args.length == 1) ? args[0] : "config.json"))));
		
		//TODO: if add logic to download files from dev sftp; and minify them... E.g. don't just assume Dev is always going to be local; it could be remote.
		//TODO: add support for ftp, SCP other protocols?		
		//Do we need SFTP at all? if so lets open up a connection and keep it open to use wherever its needed.
		JSch localJSch = null;
	    Session localSession = null;
	    Channel localChannel = null;
	    ChannelSftp localChannelSftp = null;
		if( (!config.getString("prodSFTPHost").equals("") || !config.getString("prodSFTPUser").equals("") || !config.getString("prodSFTPKeyFile").equals("") || !config.getString("prodSFTPPassword").equals("") ) && 
				 ( (config.getString("uploadJSTo").indexOf("SFTP") > -1) || (config.getString("uploadCSSTo").indexOf("SFTP") > -1) || (config.getString("uploadJSONTo").indexOf("SFTP") > -1) || 
						 (config.getString("uploadHTMLTo").indexOf("SFTP") > -1) || (config.getString("uploadXMLTo").indexOf("SFTP") > -1) || (config.getString("uploadCriticalTo").indexOf("SFTP") > -1) )){
			System.out.println("Connecting to SFTP...");
			localJSch = new JSch();
			if(!config.getString("prodSFTPKeyFile").equals("")){
				localJSch.addIdentity(config.getString("prodSFTPKeyFile"));
			}
		    localSession = localJSch.getSession(config.getString("prodSFTPUser"), config.getString("prodSFTPHost"), 22);
		    localSession.setConfig("StrictHostKeyChecking", "no");
		    if(!config.getString("prodSFTPKeyFile").equals("")){
		    	localSession.setPassword(config.getString("prodSFTPPassword"));
			}
		    localSession.connect();
		    localChannel = localSession.openChannel("sftp");
		    localChannel.connect();
		    localChannelSftp = (ChannelSftp)localChannel;
		}
		
		//TODO: add support for other popular object stores such as Azure, and Google Cloud
		ArrayList<String> s3Uploads = new ArrayList<String>(0);
		AmazonS3 s3Client = null;
		//Do we need S3 at all? if so lets open up a connection and keep it open to use wherever its needed.
		if( (!config.getString("prodS3Bucket").equals("") && !config.getString("prodS3KeyPropertiesFile").equals("") ) && 
				 ( (config.getString("uploadJSTo").indexOf("S3") > -1) || (config.getString("uploadCSSTo").indexOf("S3") > -1) || (config.getString("uploadJSONTo").indexOf("S3") > -1) || 
						 (config.getString("uploadHTMLTo").indexOf("S3") > -1) || (config.getString("uploadXMLTo").indexOf("S3") > -1) || (config.getString("uploadCriticalTo").indexOf("S3") > -1) )){
			System.out.println("Connecting to S3...");
			
	        try {
	            s3Client = AmazonS3ClientBuilder.standard()
	                    .withRegion(config.getString("prodS3Region"))
	                    .withCredentials(new PropertiesFileCredentialsProvider(config.getString("prodS3KeyPropertiesFile")))
	                    .build();
	        }catch(AmazonServiceException e) {
	            // The call was transmitted successfully, but Amazon S3 couldn't process 
	            // it, so it returned an error response.
	            e.printStackTrace();
	        }catch(SdkClientException e) {
	            // Amazon S3 couldn't be contacted for a response, or the client
	            // couldn't parse the response from Amazon S3.
	            e.printStackTrace();
	        }
		}
        
		System.out.println("Minifying Files");
		String [] minifyKeys = new String [] {"minifyJSFiles", "minifyCSSFiles", "minifyJSONFiles", "minifyHTMLFiles", "minifyXMLFiles"}; 
		String [] minifyMimeTypes = new String [] {"text/javascript", "text/css", "text/x-json", "text/html", "text/xml-mime"}; 
		HashSet<String> minFiles = new HashSet<String>();
		Map<String, String> mimeMap = IntStream.range(0, minifyKeys.length).boxed()
			    .collect(Collectors.toMap(i -> minifyKeys[i], i -> minifyMimeTypes[i]));
		for(int j = 0; j < minifyKeys.length; j++){
			JSONArray minifyFiles = config.getJSONArray(minifyKeys[j]);
			for(int i = 0; i < minifyFiles.length(); i ++){
				JSONObject file = minifyFiles.getJSONObject(i);
				minFiles.add(file.getString(JSONObject.getNames(file)[0]));
				App.minify(config.getString("devDocRoot")+JSONObject.getNames(file)[0], config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0]), minifyMimeTypes[j]);
			}
		}
		
		System.out.println("Concatenating Files");
		String [] comboTypes = new String[]{"JS","CSS", "JSON"};
		HashSet<String> concatenatedFiles = new HashSet<String>();
		for(int k = 0; k < comboTypes.length; k++){
			JSONArray concatenateFiles = config.getJSONArray("concatenate"+comboTypes[k]+"Files");
			
			for(int i = 0; i < concatenateFiles.length(); i ++){
				JSONObject comboFile = concatenateFiles.getJSONObject(i);
				OutputStream out = null;
				InputStream in = null;
				try{
					out = new FileOutputStream(config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]);
					JSONArray filesToCombine = comboFile.getJSONArray(JSONObject.getNames(comboFile)[0]);
				    for (int j = 0; j < filesToCombine.length(); j++) {
				    	concatenatedFiles.add(filesToCombine.getString(j));
				        in = new FileInputStream(config.getString("devDocRoot")+filesToCombine.getString(j));
				        int c;
				        while ((c = in.read()) != -1) 
				            out.write(c);
				        in.close();
				    }
				}catch(Exception e){
					e.printStackTrace();
				}finally{
					try{out.close();}catch(Exception e){}
					try{in.close();}catch(Exception e){}
				}
				//clean up locally generated files in the dev environment if they're going to be moved elsewhere
				if(config.getString("upload"+comboTypes[k]+"To").indexOf("SFTP") > -1){
					System.out.println("SFTP upload: "+config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]+", to: "+config.getString("prodDocRoot")+JSONObject.getNames(comboFile)[0]);
					localChannelSftp.put(config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0], config.getString("prodDocRoot")+JSONObject.getNames(comboFile)[0]);
					new File(config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]).delete();
				}
				if(config.getString("upload"+comboTypes[k]+"To").indexOf("S3") > -1){
					System.out.println("S3 upload: "+config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]+", to: "+JSONObject.getNames(comboFile)[0]);
		            PutObjectRequest request = new PutObjectRequest(config.getString("prodS3Bucket"), JSONObject.getNames(comboFile)[0], new File(config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]));
		            request.setCannedAcl(CannedAccessControlList.PublicRead);
		            ObjectMetadata metadata = new ObjectMetadata();
		            metadata.setContentType(mimeMap.get("minify"+comboTypes[k]+"Files"));
		            metadata.setHeader("Cache-Control", "max-age=31557600");
		            request.setMetadata(metadata);
		            s3Client.putObject(request);
					s3Uploads.add(JSONObject.getNames(comboFile)[0]);
					new File(config.getString("devDocRoot")+JSONObject.getNames(comboFile)[0]).delete();
				}
			}
			if(concatenateFiles.length() > 0){//delete the min files if they're being concatenated, otherwise upload & delete them if uploading is configured for this file type
				JSONArray minifyFiles = config.getJSONArray("minify"+comboTypes[k]+"Files");
				for(int i = 0; i < minifyFiles.length(); i ++){
					JSONObject file = minifyFiles.getJSONObject(i);
					if((config.getString("upload"+comboTypes[k]+"To").indexOf("SFTP") > -1) && (!concatenatedFiles.contains(file.getString(JSONObject.getNames(file)[0])))){
						System.out.println("SFTP upload: "+config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])+", to: "+config.getString("prodDocRoot")+file.getString(JSONObject.getNames(file)[0]));
						localChannelSftp.put(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0]), config.getString("prodDocRoot")+file.getString(JSONObject.getNames(file)[0]));
						new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])).delete();
					}else if(concatenatedFiles.contains(file.getString(JSONObject.getNames(file)[0]))){//Should we assume min files are no longer needed if they're concatenated into another file?
						new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])).delete();
					}
					if((config.getString("upload"+comboTypes[k]+"To").indexOf("S3") > -1) && (!concatenatedFiles.contains(file.getString(JSONObject.getNames(file)[0])))){//Should we assume min files are no longer needed if they're concatenated into another file?
						System.out.println("S3 upload: "+config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])+", to: "+file.getString(JSONObject.getNames(file)[0]));
						PutObjectRequest request = new PutObjectRequest(config.getString("prodS3Bucket"), file.getString(JSONObject.getNames(file)[0]), new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])));
						request.setCannedAcl(CannedAccessControlList.PublicRead);
			            ObjectMetadata metadata = new ObjectMetadata();
			            metadata.setContentType(mimeMap.get("minify"+comboTypes[k]+"Files"));
			            metadata.setHeader("Cache-Control", "max-age=31557600");
			            request.setMetadata(metadata);
			            s3Client.putObject(request);
						s3Uploads.add(file.getString(JSONObject.getNames(file)[0]));
					}else if(concatenatedFiles.contains(file.getString(JSONObject.getNames(file)[0]))){
						new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])).delete();
					}
				}
			}
		}
		
		String [] nonComboTypes = new String[]{"XML","HTML"};
		for(int k = 0; k < nonComboTypes.length; k++){
			JSONArray minifyFiles = config.getJSONArray("minify"+nonComboTypes[k]+"Files");
			for(int i = 0; i < minifyFiles.length(); i ++){
				JSONObject file = minifyFiles.getJSONObject(i);
				boolean uploaded = false;
				if(config.getString("upload"+nonComboTypes[k]+"To").indexOf("SFTP") > -1){
					System.out.println("SFTP upload: "+config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])+", to: "+config.getString("prodDocRoot")+file.getString(JSONObject.getNames(file)[0]));
					localChannelSftp.put(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0]), config.getString("prodDocRoot")+file.getString(JSONObject.getNames(file)[0]));
					uploaded = true;
				}
				if(config.getString("upload"+nonComboTypes[k]+"To").indexOf("S3") > -1){
					System.out.println("S3 upload: "+config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])+", to: "+file.getString(JSONObject.getNames(file)[0]));
					PutObjectRequest request = new PutObjectRequest(config.getString("prodS3Bucket"), file.getString(JSONObject.getNames(file)[0]), new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])));
					request.setCannedAcl(CannedAccessControlList.PublicRead);
		            ObjectMetadata metadata = new ObjectMetadata();
		            metadata.setContentType(mimeMap.get("minify"+nonComboTypes[k]+"Files"));
		            metadata.setHeader("Cache-Control", "max-age=31557600");
		            request.setMetadata(metadata);
		            s3Client.putObject(request);
					uploaded = true;
					s3Uploads.add(file.getString(JSONObject.getNames(file)[0]));
				}
				if(uploaded){
					new File(config.getString("devDocRoot")+file.getString(JSONObject.getNames(file)[0])).delete();
				}
			}
		}
		
		System.out.println("Inlining critical path CSS");
		JSONArray criticalInlineCSSFiles = config.getJSONArray("criticalInlineCSSFiles");
		for(int i = 0; i < criticalInlineCSSFiles.length(); i++){
			JSONObject webPage = criticalInlineCSSFiles.getJSONObject(i);
			String criticalCSS = App.getCriticalCSS(config, config.getString("devURL")+webPage.getString("web"));
			App.injectCritical(config, config.getString("devDocRoot")+webPage.getString("local"), 
					criticalCSS+(config.getJSONObject("criticalCSSAdditions").has(webPage.getString("local")) ? config.getJSONObject("criticalCSSAdditions").getString(webPage.getString("local")) : "") );	
			boolean uploaded = false;
			if(config.getString("uploadCriticalTo").indexOf("SFTP") > -1){
				System.out.println("SFTP upload: "+config.getString("devDocRoot")+webPage.getString("local")+".critical.html, to: "+config.getString("prodDocRoot")+webPage.getString("local"));
				localChannelSftp.put(config.getString("devDocRoot")+webPage.getString("local")+".critical.html", config.getString("prodDocRoot")+webPage.getString("local"));
				uploaded = true;
			}
			if(config.getString("uploadCriticalTo").indexOf("S3") > -1){
				System.out.println("S3 upload: "+config.getString("devDocRoot")+webPage.getString("local")+".critical.html, to: "+webPage.getString("local"));
				PutObjectRequest request = new PutObjectRequest(config.getString("prodS3Bucket"), webPage.getString("local"), new File(config.getString("devDocRoot")+webPage.getString("local")+".critical.html"));
				request.setCannedAcl(CannedAccessControlList.PublicRead);
	            ObjectMetadata metadata = new ObjectMetadata();
	            metadata.setContentType("text/html");//TODO: JPack should be usable with a wide range of scripting languages; Dynamic scripting languages won't work on S3, but what about templates such as handlebars; need to consider other mimeTypes
	            metadata.setHeader("Cache-Control", "max-age=31557600");
	            request.setMetadata(metadata);
	            s3Client.putObject(request);
				uploaded = true;
				s3Uploads.add(webPage.getString("local"));
			}
			if(uploaded){
				new File(config.getString("devDocRoot")+webPage.getString("local")+".critical.html").delete();
			}
		}
		
		if(localJSch != null){
			System.out.println("Disconnecting from SFTP...");
			localChannelSftp.exit();
		    localSession.disconnect();
		}
		
		//TODO: add support for other popular CDNs
		//Invalidate Cloudfront Cache
		if(!config.getString("invalidateCloudfrontDistributionID").equals("") && s3Uploads.size() > 0){
			//Cloudfront requires all paths start with a '/'
			for(int i = 0; i < s3Uploads.size(); i++){
				if(s3Uploads.get(i).trim().indexOf("/") != 0){
					s3Uploads.set(i, "/"+s3Uploads.get(i).trim());
				}
			}
			System.out.println("Invalidating Cloudfront cache for these paths: "+s3Uploads);
			Random rand = new Random();
			String uid = rand.nextInt(1000000)+"_"+(new java.util.Date().getTime());
			
			AWSCredentials awsCredentials = new PropertiesFileCredentialsProvider(config.getString("cloudfrontKeyPropertiesFile")).getCredentials();
			AmazonCloudFrontClient client = new AmazonCloudFrontClient(awsCredentials);
			com.amazonaws.services.cloudfront.model.Paths invalidation_paths = new com.amazonaws.services.cloudfront.model.Paths().withItems(s3Uploads).withQuantity(s3Uploads.size());
			InvalidationBatch invalidation_batch = new InvalidationBatch(invalidation_paths, uid);
			CreateInvalidationRequest invalidation = new CreateInvalidationRequest(config.getString("invalidateCloudfrontDistributionID"), invalidation_batch);
			CreateInvalidationResult ret = client.createInvalidation(invalidation);
		}
		
		//TODO: would be neat to have a feature that scans a set of pages and looks for dead CSS & JS code
		System.out.println("Done!");
	}
	
	private static void minify(String inputFilePath, String outputFilePath, String mimeType) {
        MinifyProperty minifyProperty = MinifyProperty.getInstance();
        MinifyUtil util = new MinifyUtil();
        HashMap<String, String> mimeTypes = new HashMap<String, String>(5);
        mimeTypes.put("text/javascript", "JS");
        mimeTypes.put("text/css", "CSS");
        mimeTypes.put("text/html", "HTML");
        mimeTypes.put("text/x-json", "JSON");
        mimeTypes.put("text/xml-mime", "XML");

        try {
        	if(mimeTypes.get(mimeType) == null){
        		throw new IOException("Unrecognized mime type: please specify either text/javascript, text/css, text/html, text/x-json, text/xml-mime");
        	}
        	
            MinifyFileResult minifyFileResult = util.compress(inputFilePath, mimeType, outputFilePath, minifyProperty);
          //TODO: add logic to optionally turn off / on print logs (e.g. verbose mode) and / or to just show aggregate stats of all files minified
            System.out.println(mimeTypes.get(mimeType)+" Minified "+new File(inputFilePath).getName()+" Completed Successfully\n"
                    + "Input "+mimeTypes.get(mimeType)+" Files Size : " + minifyFileResult.getInputFileSize() + "Bytes \n"
                    + "After Minifying "+mimeTypes.get(mimeType)+" Files Size : " + minifyFileResult.getOutputFileSize() + "Bytes \n"
                    + mimeTypes.get(mimeType)+" Space Saved " + minifyFileResult.getSavedPercentage() + "%");            
        } catch (HeadlessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
	
	//TODO: many server side scripted pages will contain different CSS depending on the state of the current user session; 
	//would be nice to provide an option to get critical CSS for several page variants 
	//(e.g. same URL but with different query parameters or session variables) and merge the CSS from all into one (removing duplicate rules); 
	//otherwise all you're really doing is improving your PSI rank without necessarily improving the user experience for all work flows.
	public static String getCriticalCSS(JSONObject config, String url) throws Exception{
		ProcessBuilder pb = new ProcessBuilder(config.getString("pathToCriticalBinary"), url);
		if(config.has("NODE_PATH") || config.has("PATH")) {
			Map<String, String> envs = pb.environment();
			if(config.has("NODE_PATH")) {
				envs.put("NODE_PATH", config.getString("NODE_PATH"));
			}
			if(config.has("PATH")) {
				envs.put("PATH", config.getString("PATH"));
			}
		}
		StringBuilder sb = new StringBuilder();	
		Process pr = pb.start(); 
		BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(pr.getInputStream())); 
			String line; 
			while ((line = br.readLine()) != null) {  
				sb.append(line+System.lineSeparator());
			}
			int i = pr.waitFor();
			br.close();
			if(i == 0){
				return App.minifyCSSString(sb.toString());
			}
		}catch (Exception e){
			e.printStackTrace();
			return null;
		}finally{
			try{br.close();}catch(Exception e){}
		}
		return null;
	}
	
	private static void injectCritical(JSONObject config, String localPath, String css){
		BufferedWriter out = null;
		BufferedReader in = null;
		try{
			out = new BufferedWriter(new FileWriter(localPath+".critical.html"));
			
	        in = new BufferedReader(new FileReader(localPath));
	        String line;
	        boolean criticalHeadCodeBlock = false, criticalFooterBlock = false;
	        while ((line = in.readLine()) != null) {
	        	if(line.trim().equals(config.getString("criticalHeaderStartComment"))){
	        		criticalHeadCodeBlock = true;
	        	}else if(line.trim().equals(config.getString("criticalFooterStartComment"))){
	        		criticalFooterBlock = true;
	        	}
	        	if(!criticalHeadCodeBlock && !criticalFooterBlock){
	        		out.write(line+System.lineSeparator());
	        	}else if(criticalHeadCodeBlock && line.trim().equals(config.getString("criticalHeaderEndComment"))){
	        		out.write(config.getString("criticalHeaderStartComment")+System.lineSeparator()+"<style type=\"text/css\">"+css+"</style>"+System.lineSeparator()+
	        				config.getString("criticalHeaderEndComment")+System.lineSeparator());
	        		criticalHeadCodeBlock = false;
	        	}else if(criticalFooterBlock && line.trim().equals(config.getString("criticalFooterEndComment"))){
	        		out.write(config.getString("criticalFooterStartComment")+System.lineSeparator()+CRITICAL_JS+System.lineSeparator()+
	        				config.getString("criticalFooterEndComment")+System.lineSeparator());
	        		criticalFooterBlock = false;
	        	}
	        }
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			try{out.close();}catch(Exception e){}
			try{in.close();}catch(Exception e){}
		}
	}
	
	private static String minifyCSSString(String content){
		MinifyProperty minifyProperty = MinifyProperty.getInstance();
        StringWriter outputWriter = null;
        Reader in = null;
        try {
        	in = new StringReader(content);
            CssCompressor compressor = new CssCompressor(in);
            in.close();
            outputWriter = new StringWriter();
            compressor.compress(outputWriter, minifyProperty.getLineBreakPosition());
            outputWriter.flush();
            return outputWriter.toString();
        } catch (HeadlessException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }finally{
        	try{outputWriter.close();}catch(Exception e){}
        	try{in.close();}catch(Exception e){}
        }
        return null;
	}
	
	//TODO this method isn't required at the moment as critical automatically handles HTML if you pass it a URL.  However critical CLI (critical does) doesn't allow you to specify html basic auth
	//login creds (e.g. for pages that need login to be viewed), however even critical doesn't allow login pages that use session cookies.  Would be neat to write Java code to 
	//simulate both of these login methods, and download the HTML files using this method & pass said HTML code to critical
	private static void downloadHTML(String urlPath, String outputFilePath){
		//TODO: this code won't download the HTML for a 404 or 500 page (e.g. if you want to inline critical CSS for your custom 404 or 500 page) (critical doesn't do this either)
		URL url;
	    InputStream ins = null;
	    BufferedReader in = null;
	    BufferedWriter bw = null;
	    InputStreamReader isr = null;

	    try {
	    	bw = new BufferedWriter(new FileWriter(outputFilePath));
	        url = new URL(urlPath);
	        if(urlPath.toLowerCase().trim().indexOf("https:") == 0){
	        	HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
		        con.setRequestProperty ( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0" );
		        ins = con.getInputStream();
	        }else if(urlPath.toLowerCase().trim().indexOf("http:") == 0){
	        	HttpURLConnection con = (HttpURLConnection) url.openConnection();
		        con.setRequestProperty ( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0" );
		        ins = con.getInputStream();
	        }else{
	        	throw new MalformedURLException("Only supported protocols are http & https");
	        }
	        
	        isr = new InputStreamReader(ins, "UTF-8");
	        in = new BufferedReader(isr);
	        String inputLine;

	        // Write each line into the file
	        while ((inputLine = in.readLine()) != null) {
	            bw.write(inputLine+System.lineSeparator());
	        }
	        in.close(); 
	        bw.close();
	    } catch (MalformedURLException mue) {
	         mue.printStackTrace();
	    } catch (IOException ioe) {
	         ioe.printStackTrace();
	    } finally {
	        try {isr.close();} catch (Exception e) {}
	        try {in.close();} catch (Exception e) {}
	        try {ins.close();} catch (Exception e) {}
	        try {bw.close();} catch (Exception e) {}
	    }
	}

}
