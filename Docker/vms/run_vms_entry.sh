#!/bin/bash
#
# Run vms.sh in Docker
# Before running this script, run `docker login` first.
# Usage: run_vms_entry.sh <projects-list> <sha-dir> <output-dir> [branch] [timeout=86400s] [test-timeout=1800s] [test-timeout=1800s] [threads=10]
#
PROJECTS_LIST=$1
SHA_DIR=$2
OUTPUT_DIR=$3
BRANCH=$4
TIMEOUT=$5
TEST_TIMEOUT=$6
THREADS=${7:-10}


SCRIPT_DIR=$(cd $(dirname $0) && pwd)

function check_input() {
  if [[ ! -d ${SHA_DIR} || ! -f ${PROJECTS_LIST} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage: run_vms_entry.sh <projects-list> <sha-dir> <output-dir> [branch] [timeout=86400s] [test-timeout=1800s] [threads=10]"
    exit 1
  fi
  
  if [[ ! ${SHA_DIR} =~ ^/.* ]]; then
    SHA_DIR=${SCRIPT_DIR}/${SHA_DIR}
  fi
  
  if [[ ! ${OUTPUT_DIR} =~ ^/.* ]]; then
    OUTPUT_DIR=${SCRIPT_DIR}/${OUTPUT_DIR}
  fi
  
  mkdir -p ${OUTPUT_DIR}
  
  if [[ ! -s ${PROJECTS_LIST} ]]; then
    echo "${PROJECTS_LIST} is empty..."
    exit 0
  fi
}

function setup_commands() {
  rm -f ${OUTPUT_DIR}/run_vms_cmd.txt

  while read -r project; do
    if [[ -z ${project} ]]; then
      continue
    fi
    
    echo "bash ${SCRIPT_DIR}/run_vms_in_docker.sh ${project} ${SHA_DIR} ${OUTPUT_DIR} ${BRANCH} ${TIMEOUT} ${TEST_TIMEOUT}" >> ${OUTPUT_DIR}/run_vms_cmd.txt
  done < ${PROJECTS_LIST}
}

check_input
setup_commands
cat ${OUTPUT_DIR}/run_vms_cmd.txt | parallel --jobs ${THREADS} --bar
