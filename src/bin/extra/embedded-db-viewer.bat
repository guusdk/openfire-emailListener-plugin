@echo off

REM # $Revision$
REM # $Date$

REM # Script to start the HSQLDB database viewer. The embedded-db.rc file 
REM # contains connection settings. Visit http://hsqldb.org for documentation
REM # on using the tool. The classpath includes JDBC drivers shipped with Wildfire
REM # to work with the Transfer tool. You will need to add any other JDBC driver
REM # that you'd like to use with the transfer tool to the classpath manually.

SET CLASSPATH=../../lib/hsqldb.jar;../../lib/hsqldbutil.jar;../../lib/mysql.jar;../../lib/postgres.jar;../../lib/jtds.jar

echo Starting ebedded database viewer...

java -cp %CLASSPATH% org.hsqldb.util.DatabaseManagerSwing --rcfile embedded-db.rc --urlid embedded-db
