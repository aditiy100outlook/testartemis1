#!/bin/bash
#* Test setup Archive42 Maintenance Interruption
#** Define Global Properties
#
#*****************************************************
#* This is Nick SH's code that is now a shell script *
#* It's needed to test Archive interruptability with *
#* Ping René or Nick Shelagno-Hegna if needed        *
#*****************************************************
#
# Rene todo list:
# 1) add input variables for the server and username/password
# 2) plan uid and archiveuid variables/input as well
#
# The archive guid to work on
archive_guid=700218316818790408
# The plan uid that belongs to the archive
planuid=700218316751681544
# How long to wait (in seconds) to interrupt maintenance (rec. 3-5 sec)
wait_time=3
# ("true"/"false") interrupt through the event store or unified api
use_old_resource="true"
# How long to wait after interrupting to kick off maint again
run_maint_interval=5
# Number of files to create
num_files=25
# Number of files to delete
num_files_to_delete=5

# STEP 1: Create a plan/archive via the console. Get the planuid/archive guid.  NOT PERFORMED IN THIS SCRIPT.
# STEP 2: Make sure you replace the server address and username/password for the admin user in this script, with the correct values of the server you're running.

clear
echo
echo "This is the Archive Maintenance Interruptability script (pl-19669)"
echo
echo "You need the Shareplan uid, the archive GUID, username and password + hostname"
echo "you have to edit these into this script for it to run for now."
echo
echo "*************************************************************************************"
echo "make sure the groovy resource is copied into place, if not then CTRL+C here and do it"
echo "before you continue!!!
echo
echo "press any key to start the script"
echo "*************************************************************************************"
read -n 1 -s
echo

# Sets up maintenance to prune deleted files immediately.  NOTE: If run on a shared server, you should reset this following the test
curl -k -u "awsadmin:\$aws@admin!" --basic --output command.out --data-binary "{command:'prop.set c42.archive42.deleted.versions.keep.latest.days 0 save all'}" --header "Content-Type: application/json" --header "Accept: application/json" https://aws.test.code42.com:4285/api/cli


# Create a bunch of files with random data (locally)
echo "making some files in TestInterrupt folder"
cd ~/rene
mkdir TestInterrupt
cd TestInterrupt
for i in `seq 1 $num_files`
do
    dd if=/dev/urandom of="file${i}.txt" bs=1048576 count=$i
done


# Upload the files to the server
echo "uploading files to the server"
for i in $( ls )
do
    curl -k -vv --form-string planUid=$planuid -F "file=@$i;type=file/type"\
         -u awsadmin:\$aws@admin! https://aws.test.code42.com:4285/api/file
done


# Delete file(1-10).txt
echo "Deleting some of the files"
for i in `seq 1 $num_file_to_deleted`
do
    curl -k -X DELETE -u "awsadmin:\$aws@admin!" https://aws.test.code42.com:4285/api/file/$planuid/file${i}.txt
done


# Run and then interrupt maintenance 20 times
echo "interrupting maintenance 20 times"
for i in `seq 1 20`
do
    echo "Kicking off maintenance in non required mode."

    curl -k -k -X POST -u "awsadmin:\$aws@admin!" -H 'Content-Type:application/json' -d {"archiveGuid":"$archive_guid"} https://aws.test.code42.com:4285/api/MaintRunInterruptableTest

    sleep $wait_time

    if [ $use_old_resource == "true" ]
    then
        curl -k -u "awsadmin:\$aws@admin!" https://aws.test.code42.com:4285/api/FileInfo/$planuid
    else
        curl -k -u "awsadmin:\$aws@admin!" https://aws.test.code42.com:4285/api/FileMetadata/$planuid
    fi

    sleep $run_maint_interval
done


# Get the contents from the archive as a zip file
umask 022
 echo "Now grabbing the archive content.."
  cd ~/TestInterrupt
  mkdir AfterMaintArchive
  cd ./AfterMaintArchive
curl -k -LOk -u "awsadmin:\$aws@admin!" https://aws.test.code42.com:4285/api/FileContent/$planuid?zipFolderContents=true
  mkdir Unzipped
  cd Unzipped
  unzip ../$planuid*
echo
  #Iterate over the files that were returned and compare md5s
 echo "Now comparing the md5 sums of initial and final.."

#Iterate over the files that were returned and compare md5s
for i in $( ls )
do
    returned_src=`md5sum $i | awk '{print $1}'`
    returned_src=${returned_src#*= }
echo $returned_src
    original_src=`md5sum ../../$i | awk '{print $1}'`
    original_src=${original_src#*= }
echo $original_src
    #if [ "$returned_src" != "$original_src" ]
    if [ $returned_src != $original_src ]
    then
        echo "FAIL"
                echo
    else
        echo "PASS"
                echo
    fi
done

# We should expect that the versions that were deleted are not in the returned results
for i in `seq 1 5`
do
    file="./file${i}.txt"
    [ -f $file ] && echo "UNPRUNED FILE FOUND"
done

echo
echo "Clean up after yourself, doublecheck the folder and rm -rf TestInterrupt"
echo "Script is now done"
echo


