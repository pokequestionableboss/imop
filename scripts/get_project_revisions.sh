#!/bin/bash
#
# Select projects and SHAs for evaluation
# Usage: get_project_revisions.sh <revisions> <repo> <sha> <output-dir> <log-dir>
# Run test, mop, and iemop
#
REVISIONS=$1
REPO=$2
SHA=$3
OUTPUT_DIR=$4
LOG_DIR=$5
SCRIPT_DIR=$(cd $(dirname $0) && pwd)
PROJECT_NAME=$(echo ${REPO} | tr / -)

EXTENSIONS_DIR=${SCRIPT_DIR}/../extensions
MOP_DIR=${SCRIPT_DIR}/../mop

source ${SCRIPT_DIR}/constants.sh

function check_input() {
  if [[ -z ${REVISIONS} || -z ${REPO} || -z ${SHA} || -z ${OUTPUT_DIR} || -z ${LOG_DIR} ]]; then
    echo "Usage bash get_project_revisions.sh <revisions> <repo> <sha> <output-dir> <log-dir>"
    exit 1
  fi
  
  if [[ ! ${OUTPUT_DIR} =~ ^/.* ]]; then
    OUTPUT_DIR=${SCRIPT_DIR}/${OUTPUT_DIR}
  fi
  
  if [[ ! ${LOG_DIR} =~ ^/.* ]]; then
    LOG_DIR=${SCRIPT_DIR}/${LOG_DIR}
  fi
  
  mkdir -p ${OUTPUT_DIR}
  mkdir -p ${OUTPUT_DIR}/repo
  mkdir -p ${LOG_DIR}
}

function setup() {
  log "Setting up environment..."
  mvn -Dmaven.repo.local=${OUTPUT_DIR}/repo install:install-file -Dfile=${MOP_DIR}/agents/no-track-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar"
  
  pushd ${SCRIPT_DIR}/../iemop-maven-plugin &> /dev/null
  mvn -Dmaven.repo.local=${OUTPUT_DIR}/repo install
  popd &> /dev/null
}

function log() {
  local message=$1
  echo "${message}" |& tee -a ${LOG_DIR}/output.log
}

function test_commit() {
  local sha=$1
  mkdir -p ${LOG_DIR}/${sha}
  git checkout ${sha} &> /dev/null
  
  run_compile ${sha}
  if [[ $? -ne 0 ]]; then
    echo "${sha},0,0,0" >> ${LOG_DIR}/commits-check.txt
    
    log "Cannot use commit ${sha} due to compile error"
    return 1
  fi
  
  run_test ${sha}
  if [[ $? -ne 0 ]]; then
    echo "${sha},1,0,0" >> ${LOG_DIR}/commits-check.txt
    
    log "Cannot use commit ${sha} due to test error"
    return 1
  fi
  
  run_test_with_rv ${sha}
  if [[ $? -ne 0 ]]; then
    echo "${sha},1,1,0" >> ${LOG_DIR}/commits-check.txt
    
    log "Cannot use commit ${sha} due to iemop error"
    return 1
  fi
  
  echo "${sha},1,1,1" >> ${LOG_DIR}/commits-check.txt
  log "Finished testing commit ${sha}"
  return 0
}

function run_compile() {
  local sha=$1
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Running test-compile"
  local start=$(date +%s%3N)
  (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${OUTPUT_DIR}/repo ${SKIP} clean test-compile) &>> ${LOG_DIR}/${sha}/compile.log
  local status=$?
  local end=$(date +%s%3N)
  local duration=$((end - start))
  echo "[IEMOP] Duration: ${duration} ms, status: ${status}" |& tee -a ${LOG_DIR}/${sha}/compile.log
  
  mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${OUTPUT_DIR}/repo ${SKIP} dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q 2>&1 | cat > ${LOG_DIR}/${sha}/classpath.log
  return ${status}
}

function run_test() {
  local sha=$1
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Running test without MOP"
  local start=$(date +%s%3N)
  (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${OUTPUT_DIR}/repo ${SKIP} surefire:test) &>> ${LOG_DIR}/${sha}/test.log
  local status=$?
  local end=$(date +%s%3N)
  local duration=$((end - start))
  echo "[IEMOP] Duration: ${duration} ms, status: ${status}" |& tee -a ${LOG_DIR}/${sha}/test.log
  
  return ${status}
}

function run_test_with_rv() {
  local sha=$1
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  # Backup project and repo
  rm -rf /tmp/project /tmp/repo
  cp -a ${OUTPUT_DIR}/repo /tmp/repo
  cp -a ${OUTPUT_DIR}/project /tmp/project

  log "Running test with iemop"
  local start=$(date +%s%3N)
  
  export ADD_IEMOP_EXTENSION=1
  (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${OUTPUT_DIR}/repo ${SKIP} -Dmaven.ext.class.path=${EXTENSIONS_DIR}/iemop-maven-extension-1.0.jar -Dthreads=${THREADS} -Dvariant=online -Dtimer=true org.iemop:iemop-maven-plugin:1.0:run) &>> ${LOG_DIR}/${sha}/iemop.log
  local status=$?
  unset ADD_IEMOP_EXTENSION
  local end=$(date +%s%3N)
  local duration=$((end - start))
  
  if [[ ${status} -ne 0 ]]; then
    echo "[IEMOP] Restoring a failed iemop run..."
    cd  ${OUTPUT_DIR}
    rm -rf ${OUTPUT_DIR}/repo ${OUTPUT_DIR}/project
    cp -a /tmp/project ${OUTPUT_DIR}/project
    cp -a /tmp/repo ${OUTPUT_DIR}/repo
    cd ${OUTPUT_DIR}/project
  fi
  echo "[IEMOP] Duration: ${duration} ms, status: ${status}" |& tee -a ${LOG_DIR}/${sha}/iemop.log

  return ${status}
}

function get_project() {
  pushd ${OUTPUT_DIR} &> /dev/null
  
  export GIT_TERMINAL_PROMPT=0
  git clone https://github.com/${REPO} project |& tee -a ${LOG_DIR}/output.log
  pushd ${OUTPUT_DIR}/project &> /dev/null
  git checkout ${SHA} |& tee -a ${LOG_DIR}/output.log
  if [[ $? -ne 0 ]]; then
    log "Skip project: cannot clone repository"
    exit 1
  fi
  
  if [[ -f ${OUTPUT_DIR}/project/.gitmodules ]]; then
    log "Skip project: project contains submodule"
    exit 1
  fi

  echo ${SHA} > ${LOG_DIR}/commits.txt
  # git rev-list --first-parent --topo-order --remove-empty --no-merges --branches=master -n ${GIT_LOG_REVS} HEAD)
  git log --name-status | grep 'java\|^commit' | grep -B1 'java$' | grep ^commit | cut -d ' ' -f 2 | head -n 500 >> ${LOG_DIR}/commits.txt
  local failure=0
  
  while read -r commit; do
    log "Testing commit ${commit}"
    test_commit ${commit}
    if [[ $? -ne 0 ]]; then
      failure=$((failure + 1))
      if [[ ${failure} -ge 10 ]]; then
        log "Skip project: 10 failures in a row"
        exit 1
      fi
    else
      failure=0
    fi

    local success=$(grep ,1,1,1 ${LOG_DIR}/commits-check.txt | wc -l)
    if [[ ${success} -ge ${REVISIONS} ]]; then
      break
    fi
    log "Found ${success} projects"
  done < ${LOG_DIR}/commits.txt
  
  popd &> /dev/null
  popd &> /dev/null
  
  log "Done, found $(grep ,1,1,1 ${LOG_DIR}/commits-check.txt | wc -l)/$(cat ${LOG_DIR}/commits-check.txt | wc -l) valid commits"
}

export RVMLOGGINGLEVEL=UNIQUE
check_input
setup
get_project
