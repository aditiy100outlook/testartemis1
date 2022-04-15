#!/bin/bash

# when backing up, monitor your logs with: tail -f service.log.0 | grep -E -e ".*BT::.*bytes sent.*"

# default values
TEST_FILE=smallTestData
FILE_SIZE_MB=128


print_usage() {
  echo "Usage: createLargeTestData [-c] [-s sizeInMB] [-m] [-b]"
  echo "If invoked with no parameters the test file will be created if it doesn't exist."
  echo " -c    create a new test file, erasing the existing one if it exists"
  echo " -s    SIZE size in MB of the desired test file"
  echo " -m    modify data contained within an existing file"
  echo " -b    byte-shift an existing test file by prefixing the existing data with 1 byte"
}

while getopts ":hcs:mb" optname ; do
  case "$optname" in
    "h")
      print_usage
			exit 0
      ;;
    "c")
      CREATE_OPT=CREATE
      ;;
    "s")
			FILE_SIZE_MB=$OPTARG
      ;;
    "m")
      MODIFY_OPT=MODIFY_OPT
      ;;
    "b")
      BYTE_SHIFT_OPT=BYTE_SHIFT
      ;;
    *) # Should not occur
      echo "Unknown error while processing options"
			exit 1
      ;;
  esac
done

# dd block size (used for create and modify). DO NOT CHANGE THIS .. all the math depends on it
DD_BLOCK_SIZE=1024

# calculate how many blocks we need to get the MB size they wanted
DD_NUM_KB_BLOCKS=$(($FILE_SIZE_MB * 1024))

# modification params- we make 2 modifications per MB of file size
MODIFICATION_COUNT=$((2 * $FILE_SIZE_MB))

# our modifications are EVEN or ODD in size, do not use a power of 2
DD_MOD_NUM_KB_BLOCKS_EVEN=11
DD_MOD_NUM_KB_BLOCKS_ODD=53

# our modifications are offset by EVEN or ODD num blocks, do not use a power of 2
DD_MOD_SEEK_NUM_BLOCKS_OFFSET_EVEN=15
DD_MOD_SEEK_NUM_BLOCKS_OFFSET_ODD=57

create() {
  dd if=/dev/urandom of=${TEST_FILE} count=${DD_NUM_KB_BLOCKS} bs=${DD_BLOCK_SIZE}
}


modify() {
  count=0
  MAXCOUNT=$(($MODIFICATION_COUNT - 1))
  SEEK=0
  while [ "$count" -le $MAXCOUNT ]
  do
  	echo "  $count of $MAXCOUNT - seek to ${SEEK}"

		# is this an even or an odd run?
		offset=$DD_MOD_SEEK_NUM_BLOCKS_OFFSET_ODD
		num_blocks=$DD_MOD_NUM_KB_BLOCKS_ODD
		even=$(($count % 2))
		if [ "$even" -le 0 ] ; then
			offset=$DD_MOD_SEEK_NUM_BLOCKS_OFFSET_EVEN
			num_blocks=$DD_MOD_NUM_KB_BLOCKS_EVEN
		fi
		
    dd if=/dev/urandom of=${TEST_FILE} seek=${SEEK} bs=${DD_BLOCK_SIZE} count=${num_blocks} conv=notrunc

    let "count += 1"
  	SEEK=$(($SEEK + ${num_blocks} + ${offset}))
  done
}


byte_shift() {
	FILE=$1
	
	dd if=/dev/urandom of=${FILE}.prefix count=1 bs=1
	echo 
	echo " .. prefix file details:"
	echo `ls -al ${FILE}.prefix`

	echo 
	echo " .. appending $FILE to the prefix byte"
	cat $FILE >> ${FILE}.prefix
	echo 
	echo " .. post append stats:"
	ls -al ${FILE}*

  echo 
	mv -f ${FILE}.prefix $FILE
	echo " .. replaced original file:"
	ls -al ${FILE}*
}


echo 
echo "== Small Test Data Mods =="
echo 

# are we recreating the file?
if [ -n "$CREATE_OPT" ] ; then
	rm -rf ${TEST_FILE}
fi

if [ ! -e ${TEST_FILE} ] ; then
	echo 
  echo " creating ${TEST_FILE} in current directory"
	create
	echo 
  echo " .. $TEST_FILE created: "
  pwd
  ls -alh $TEST_FILE
  FILE_MOD_DONE=1
fi

if [ -n "$MODIFY_OPT" ] ; then
  echo " modifying ${TEST_FILE}"
  sleep 2

  modify
  echo
  echo " .. ${TEST_FILE} modifications complete"
  FILE_MOD_DONE=1
fi

if [ -n "$BYTE_SHIFT_OPT" ] ; then
	echo " byte shifting ${TEST_FILE}"
	byte_shift ${TEST_FILE}
	echo
	echo " .. $TEST_FILE byte shift complete"
  FILE_MOD_DONE=1
fi

if [ -z "$FILE_MOD_DONE" ] ; then
  echo 
  echo ERROR: File exists, no modifications specified.
  echo 
  print_usage
fi

exit 0
