# JPack
A Java based web packaging tool for optimizing assets for websites and enhancing Google Pagespeed Insights (PSI) score.  Although written in Java, this software is designed to work for any web stack that can render HTML e.g. .NET, RoR, PHP, JSP, handlebars etc...  But was built with projects in mind that have server side code.  You can build this project using Maven, or download a pre-built binary here: [https://github.com/gr9/JPack/releases/download/v0.1-beta/JPack.jar](https://github.com/gr9/JPack/releases/download/v0.1-beta/JPack.jar)

This isn't designed to replace something like Webpack and in many cases isn't going to stack up against something as well supported or as featureful as Webpack.  However, Webpack is best positioned for using the Node stack, and when you're looking at optimizing for web stacks that have a server side component (that isn't node) this can be tricky: [https://stackoverflow.com/questions/47833812/webpack-with-jsp-index-file](https://stackoverflow.com/questions/47833812/webpack-with-jsp-index-file) particularly for inlining critical path CSS.  Ironically though, the best (only?) tools for inlining critical path CSS are all npm modules.  Google Closure Compiler does work best when run with the JVM installed though - so its 6 of one & half a dozen of the other.

The concept is pretty simple, you develop in your dev environment - for which you need human readable code (e.g. not minified JS files, or critical path inline CSS).  Once you have it working how you want though - you need to pack it up for speed, and don't need to work with the code directly after its been deployed to prod.  To this end, JPack will take the files you want optimized from your dev environment and deploy them to prod for you in a way that will help your PSI score.

## What It Does

* Minify JS, CSS, XML, HTML, and JSON
* Compile JS files to optimize them and issue errors, warnings using [https://developers.google.com/closure/compiler/](https://developers.google.com/closure/compiler/) Google Closure Compiler (e.g. unreachable code branch etc...)
* Concatenates JS, CSS, and JSON files
* Performs critical path CSS inlining [https://www.npmjs.com/package/critical](https://www.npmjs.com/package/critical)
* Calculates Space Savings in minified files
* Automatically deploy files to SFTP, AWS S3 & Cloudfront
* Sets S3 metadada to include cache policies recommended by PSI, an optionally allows you to invalidate Cloudfront cache if you're using S3 as the origin for your distribution

## What It Doesn't Do

* A bunch of things mentioned in the //TODO comments of App.java - feel free to create a pull request for anything mentioned in these comments; e.g. anything that makes JPack more general purpose / versatile
* Its not designed to replace your server side logic aside from injecting critical path CSS where you tell it to

## Requirements
1. config.json must be either in the same folder that JPack is run from, or the path to config.json must be passed as a command line argument (see Usage below)
2. Node and critical CLI have to be installed on the dev environment that you run JPack from - [https://github.com/addyosmani/critical](https://github.com/addyosmani/critical) Required, only if you want to use the critical path CSS features
3. Java Runtime Environment (JRE) must be installed on your dev environment that you run JPack from

## Usage
*Please make sure to commit your latest code changes & backup your prod environment prior to use - this is still in beta mode & I'd feel awful if this script killed your site or lost any of your recent changes.*

Suggested changes to your server side code using JSP as an example:


`

		<% if(request.getServerName().equals("dev.my-dev-url.com")){ %> 
        <link rel="stylesheet" href="/css/style.css" type="text/css" />
        <link rel="stylesheet" href="/css/style-2x.css" type="text/css" />
        <link rel="stylesheet" href="/css/rwd.css" type="text/css" />
        <script type="text/javascript" src="/js/global.js"></script>
        <script type="text/javascript" src="/js/page-specific.js"></script>
        <% }else { %>
        <!--Critical CSS Starts Here-->
        <!--Critical CSS Ends Here-->
        <link rel="preload" href="https://xyz.cloudfront.net/css/combo-min.css" type="text/css" as="style" onload="this.rel='stylesheet'">
        <noscript><link rel="stylesheet" href="https://xyz.cloudfront.net/css/combo-min.css" type="text/css"></noscript>
        <script type="text/javascript" src="https://xyz.cloudfront.net/js/public-combo-min.js"></script>  
        <% } %>
        `

Run JPack from the command line:

`java -jar JPack.jar [optional-path-to-config.json]`   

## config.json     
A sample config.json is included in the source; here's an explanation of the configuration variables:

* **devURL** - The base URL to your dev environment.  This is required if you want to create critical path CSS inlining (e.g. JPack will look at your dev site and figure out what the critical path CSS is)
* **devDocRoot** - The local document root of your website files.  Required
* **prodDocRoot** - The remote document root of your prod website (or the website you want JPack to push optimized files to e.g. QA, UAT etc...).  Required only if you want files uploaded by SFTP to a remote server.  If not specified & if 'SFTP' isn't specified as an upload mode for one of the filetypes, then files will just remain on your local dev environment
* **minifyJSFiles**, **minifyCSSFiles**, **minifyJSONFiles**, **minifyHTMLFiles**, **minifyXMLFiles** - A JSON array containing JSON objects.  Each JSON object represents a file you want to have minified.  The JSON Object only contains one key and one value.  The key is the path of the file you want to minify (with its path relative to devDocRoot), the value is the path of minified file you want to create (relative to devDocRoot).  These are optional, only use them for the file(s) and file type(s) you want to minify.
* **prodSFTPHost** - The host value to use for the SFTP connection.  Required if you set 'SFTP' to be an upload mode for any file type.
* **prodSFTPUser** - The Linux user name to use for the SFTP connection.  Required if you set 'SFTP' to be an upload mode for any file type.
* **prodSFTPKeyFile** - The absolute path to the key file you want to use for the SFTP connection.  Required if you set 'SFTP' to be an upload mode for any file type.
* **prodSFTPPassword** - The password you want to use for the SFTP connection.  You can use this instead of prodSFTPKeyFile.  Required if you set 'SFTP' to be an upload mode for any file type and if you're not using a key file.
* **concatenateJSFiles**, **concatenateCSSFiles**, **concatenateJSONFiles** - A JSON array containing JSON objects.  Each JSON object represents a file that is the concatenation of 1 or more other files.  Each JSON object must contain 1 key whose value is a JSON array containing at least 1 string.  The key is the path name of what you want the concatenated file to be called (relative to devDocRoot).  Each string in the JSON array value is the path of a file (relative to devDocRoot) that you want to concatenate.  Optional, required only if you want to concatenate multiple files into 1 file.
* **prodS3Bucket** - The AWS S3 bucket name you want to upload optimized files to.  Optional, required only want to upload optimized files to S3 and you set 'S3' as the upload mode for any file type
* **prodS3KeyPropertiesFile** - Absolute file path to an AWS properties file for an IAM role or Root key that has permissions to write to prodS3Bucket.  First line of the props file should be `accessKey=xyz` second line of the file should be `secretKey=abc`. Optional, required only want to upload optimized files to S3 and you set 'S3' as the upload mode for any file type
* **prodS3Region** - The AWS region of prodS3Bucket e.g. us-west-1. Optional, required only want to upload optimized files to S3 and you set 'S3' as the upload mode for any file type
* **uploadJSTo**, **uploadCSSTo**, **uploadJSONTo**, **uploadXMLTo**, **uploadHTMLTo** - A comma separated string containing the desired upload types.  E.g. can be an empty string "", "SFTP", "S3", or "SFTP,S3" if you want to upload to both.  Optimized files will be deleted from the local dev environment after upload if an upload type is specified, otherwise they will be retained so you can manually do something with them.  Required (can be empty string though)
* **criticalInlineCSSFiles** - A JSON array of JSON objects.  Each JSON object in the array represents 1 web page you want to have critical path CSS generated for.  Each JSON object must contain 2 key value pairs.  The first key must be called "web" and its value should be the path of the URL (relative to devURL) of the web page you want to create critical path CSS for.  The second key must be called "local" and its value should be the path (relative to devDocRoot) of the file you want to inject the critical path css into.  Optional, required only if you want to generate critical path CSS.
* **pathToCriticalBinary** - The absolute path to where the critical CLI binary is installed on the dev environment where JPack is being run.  If you're running OS X or Linux and have mlocate installed you could find this using the command: `locate critical | grep "/critical$"`    Required only if you want to create critical path CSS
* **NODE_PATH** - Where node.js is installed on your dev machine that is running JPack.  On OS X or Linux you're likely to find this in `~/.bash_profile`  Required only if you want to create critical path CSS, even then this is optional as some environments don't require it.
* **PATH** - This is the PATH environment variable that JPack temporarily creates so it can run the critical CLI program.  If you're having trouble getting critical CSS inlining to work make sure to check this and add any paths that Node.js would require to run.  This path must also include the containing folder for pathToCriticalBinary.  Required only if you want to create critical path CSS, even then this is optional as some environments don't require it.
* **criticalHeaderStartComment** - When JPack injects the critical path CSS into your code files, it needs to know where to put it.  It looks for a specific comment in your code file in order to do this.  This comment should be on its own line in your code file. Required only if you want to create critical path CSS.
* **criticalHeaderEndComment** - Same as above, looks for the end comment which also must be on its own line; this way JPack knows where to continue own with your other code. Required only if you want to create critical path CSS.
* **criticalFooterStartComment**, **criticalFooterEndComment** - Critical path CSS performs best when your non-critical CSS is loaded asynchronously.  Thus as shown above in the Usage section when you link to your non-critical CSS you should do so like this:

`<link rel="preload" href="https://xyz.cloudfront.net/css/combo-min.css" type="text/css" as="style" onload="this.rel='stylesheet'"><noscript><link rel="stylesheet" href="https://xyz.cloudfront.net/css/combo-min.css" type="text/css"></noscript>`

If you link to your non-critical style sheet(s) this way then you should put a criticalFooterStartComment and criticalFooterEndComment before the `</body>` tag of your HTML.  This way JPack will inject a piece of JavaScript into the file so as to asynchronously load your non-critical CSS.  As with the header comments, they should both have their own line (e.g. no other code on the same line). Required only if you want to asynchronously load your non-critical CSS using JavaScript.

* **uploadCriticalTo** - A comma separated string containing the desired upload types.  E.g. can be an empty string "", "SFTP", "S3", or "SFTP,S3" if you want to upload to both.  Files with critical path CSS injected into them will be deleted from the local dev environment after upload if an upload type is specified, otherwise they will be retained so you can manually do something with them.  Required (can be empty string though)
* **criticalCSSAdditions** - critical doesn't always figure out all of the CSS your page requires.  Because it asynchronously defers the loading of what it deems to be non-critical CSS, it may break some widgets or code.  If you want to add CSS to the critical path CSS calculated by critical CLI; you can do so here.  A JSON object is expected where each key is the same relative file path given as the "local" key that is given as part of criticalInlineCSSFiles.  The value of this key should be the additional CSS you want to append to the critical path CSS for a given file.  Required only if you require this feature otherwise you can leave this as an empty JSON object
* **invalidateCloudfrontDistributionID** - If you are using S3 as your origin for a Cloudfront distribution, you can put your distribution ID here & JPack will automatically create a Cloudfront cache invalidation for those files that it uploaded to S3.  Optional, only required if you want to invalidate your Cloudfront cache
* **cloudfrontKeyPropertiesFile** - Absolute file path to an AWS properties file for an IAM role or Root key that has permissions to write to invalidate Cloudfront distribution specified in invalidateCloudfrontDistributionID.  First line of the props file should be `accessKey=xyz` second line of the file should be `secretKey=abc`. Optional, only required if you want to invalidate your Cloudfront cache

## Credits
* [https://github.com/addyosmani/critical](https://github.com/addyosmani/critical) - a great piece of web tooling
* [https://github.com/Chris2011/minifierbeans](https://github.com/Chris2011/minifierbeans) - I borrowed a decent amount of code from this person; basically the same thing as JPack except its a netbeans plugin rather than a general purpose devops tool; and I added some concatenation, AWS & SFTP features
       