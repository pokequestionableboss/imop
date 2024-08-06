#!/bin/bash
#
# Run vms.sh in Docker
# Before running this script, run `docker login`
# Usage: run_vms_in_docker.sh <project> <sha-dir> <output-dir> [branch] [timeout=86400s] [test-timeout=1800s]
# sha directory must contains file: <project-name>.txt
#
PROJECT=$1
SHA_DIR=$2
OUTPUT_DIR=$3
BRANCH=$4
TIMEOUT=$5
TEST_TIMEOUT=$6

function check_input() {
  if [[ ! -d ${SHA_DIR} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage: run_vms_in_docker.sh <project> <sha-dir> <output-dir> [branch] [timeout=86400s] [test-timeout=1800s]"
    exit 1
  fi

  if [[ ! ${SHA_DIR} =~ ^/.* ]]; then
    SHA_DIR=${SCRIPT_DIR}/${SHA_DIR}
  fi

  if [[ ! ${OUTPUT_DIR} =~ ^/.* ]]; then
    OUTPUT_DIR=${SCRIPT_DIR}/${OUTPUT_DIR}
  fi

  mkdir -p ${OUTPUT_DIR}

  if [[ -z ${TIMEOUT} ]]; then
    TIMEOUT=86400s
  fi
}


function run_project() {
  local repo=$PROJECT
  local project_name=$(echo ${repo} | tr / -)

  if [[ ! -f ${SHA_DIR}/${project_name}.txt ]]; then
    echo "Skip ${project_name} because its sha file is not in sha-directory"
    return
  fi

  echo "Running ${project_name}"
  mkdir -p ${OUTPUT_DIR}/${project_name}

  local id=$(docker run -itd --name ${project_name} imop:latest)
  docker exec -w /home/iemop/iemop ${id} git pull
  
  if [[ -n ${BRANCH} && ${BRANCH} != "false" ]]; then
    docker exec -w /home/iemop/iemop ${id} git checkout ${BRANCH}
    docker exec -w /home/iemop/iemop ${id} git pull
  fi
  
  if [[ -n ${TEST_TIMEOUT} ]]; then
    echo "Setting test timeout to ${TEST_TIMEOUT}"
    docker exec -w /home/iemop/iemop ${id} sed -i "s/TIMEOUT=.*/TIMEOUT=${TEST_TIMEOUT}/" scripts/constants.sh
  fi

  docker cp ${SHA_DIR}/${project_name}.txt ${id}:/home/iemop/sha.txt

  timeout ${TIMEOUT} docker exec -w /home/iemop/iemop -e M2_HOME=/home/iemop/apache-maven -e MAVEN_HOME=/home/iemop/apache-maven -e CLASSPATH=/home/iemop/aspectj-1.9.7/lib/aspectjtools.jar:/home/iemop/aspectj-1.9.7/lib/aspectjrt.jar:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar: -e PATH=/home/iemop/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/home/iemop/aspectj-1.9.7/bin:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ${id} timeout ${TIMEOUT} bash scripts/vms.sh /home/iemop/sha.txt ${repo} /home/iemop/output &> ${OUTPUT_DIR}/${project_name}/docker.log

  docker cp ${id}:/home/iemop/output ${OUTPUT_DIR}/${project_name}/output

  docker rm -f ${id}
}

check_input
run_project
