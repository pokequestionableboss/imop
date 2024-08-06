#!/bin/bash
#
# Run get_project_revisions.sh in Docker
# Before running this script, run `docker login` first.
# Usage: get_project_revisions_in_docker.sh <project> <revisions> <output-dir> [timeout=86400s] [test-timeout=1800s]
#
PROJECT=$1
REVISIONS=$2
OUTPUT_DIR=$3
TIMEOUT=$4
TEST_TIMEOUT=$5

function check_input() {
  if [[ -z ${PROJECT} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage: get_project_revisions_in_docker.sh <project> <revisions> <output-dir> [timeout=86400s] [test-timeout=1800s]"
    exit 1
  fi
  
  mkdir -p ${OUTPUT_DIR}
  
  if [[ -z ${TIMEOUT} ]]; then
    TIMEOUT=86400s
  fi
}


function run_project() {
  local repo=$(echo ${PROJECT} | cut -d ',' -f 1)
  local sha=$(echo ${PROJECT} | cut -d ',' -f 2)
  local project_name=$(echo ${repo} | tr / -)
  
  echo "Running ${project}"
  mkdir -p ${OUTPUT_DIR}/${project_name}
  
  local id=$(docker run -itd --name ${project_name} imop:latest)
  docker exec -w /home/iemop/iemop ${id} git pull
  
  if [[ -n ${TEST_TIMEOUT} ]]; then
    echo "Setting test timeout to ${TEST_TIMEOUT}"
    docker exec -w /home/iemop/iemop ${id} sed -i "s/TIMEOUT=.*/TIMEOUT=${TEST_TIMEOUT}/" scripts/constants.sh
  fi
  
  timeout ${TIMEOUT} docker exec -w /home/iemop/iemop -e M2_HOME=/home/iemop/apache-maven -e MAVEN_HOME=/home/iemop/apache-maven -e CLASSPATH=/home/iemop/aspectj-1.9.7/lib/aspectjtools.jar:/home/iemop/aspectj-1.9.7/lib/aspectjrt.jar:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar: -e PATH=/home/iemop/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/home/iemop/aspectj-1.9.7/bin:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ${id} timeout ${TIMEOUT} bash scripts/get_project_revisions.sh ${REVISIONS} ${repo} ${sha} /home/iemop/output /home/iemop/logs &> ${OUTPUT_DIR}/${project_name}/docker.log
  
  docker cp ${id}:/home/iemop/output ${OUTPUT_DIR}/${project_name}/output
  docker cp ${id}:/home/iemop/logs ${OUTPUT_DIR}/${project_name}/logs
  
  docker rm -f ${id}
}

check_input
run_project
