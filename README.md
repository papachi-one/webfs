# WebFS
Remote file system exposed by a PHP script mapped as local file system.
## How it works
Uploaded PHP script to a web server is used to access server's file system.
PHP script implements REST API for basic file system operation (e.g. list, create, read, write, delete, ...).
WebFS (local app) mounts a virtual file system and delegates all file system operations to the PHP script via HTTP requests.
This integration allows to work with files and folders on your remote server as if they were local.
## Use cases
- Working on a web site without the need to upload it everytime you make a change and have it ready to test during development.
- Using remote server's disk space to store your files (e.g. backup).
## How to use
- Upload the PHP script to any web server that can serve PHP web pages.
- WebFS is a Java application and can be run from command prompt/console as follows:
```shell
java -jar webfs-1.0.0.jar --src=./ --dst=./wfs --url=http://localhost/rfs.php --secret=password
```
- This mounts `./` path from the point of view of the PHP script to a local path `./wfs` relative to your working directory.
- To change the secret/password, modify PHP script to use your own secret/password.