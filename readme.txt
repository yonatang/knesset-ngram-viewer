Usage (everything requires Java 7, all files should be in utf-8 encoding)

1. Download the plenum files (as xmls) into a single directory via oknesset.org API - for 
example, http://oknesset.org/api/committeemeeting/7340/?format=xml to download plenum 
protocol #7340.

2. Unzip cruncher.zip, and execute the cruncher/bin/cruncher[.sh/.cmd] to parse the .xml files:
cruncher.sh <in_directory> <out_directory>
This might take a while. The process can be speed-up, if the in directory is a shared directory (i.e. 
nfs mounted directory), and several machines will run the cruncher, targeting the same shared directory.
The cruncher is designed to work in such environment. 

3. Deploy the viewer.war into a tomcat container *running with Java 7* (tested with tomcat7 - will 
probably work with tomcat6 as well). That will also initiate the internal database.
The database storage can be defined using the h2.url property (i.e. in the tomcat jvm args,
add -Dh2.url=tcp://localhost/~/knesset to use ~/knesset.h2.db as a storage).

4. Unzip indexer.zip, and run the indexer:
indexer[.sh/.cmd] -f <json_directory> -n <ngram_size> [-dbUrl <url>]
<json_directory>  is where the .json files that were created in step 2 located
<ngram_size> is the size of the n-gram to create. For example, if -n 3 is passed, ngrams of size 1,2,3
will be created.
The default db connection string is "tcp://localhost/~/knesset". You can specify different one 
using the optional -dbUrl parameter. It should take few minutes to execute.  

5. execute the viewer: http://localhost:8080/viewer

