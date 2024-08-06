#!/bin/bash
#
# Use VMS to get new violations
# Usage: vms.sh <SHAS-FILE> <repo> <output-dir>
#
SHAS_FILE=$1
REPO=$2
PROJECT_NAME=$(echo ${REPO} | tr / -)
OUTPUT_DIR=$3
SCRIPT_DIR=$(cd $(dirname $0) && pwd)

export PREFIX="[IEMOP]"

REPO_DIR=${OUTPUT_DIR}/repo
LOG_DIR=${OUTPUT_DIR}/logs
PROJECT_DIR=${OUTPUT_DIR}/project
EXTENSION_PATH=${SCRIPT_DIR}/../extensions/

source ${SCRIPT_DIR}/constants.sh
source ${SCRIPT_DIR}/utils.sh
source ${SCRIPT_DIR}/treat_special.sh # required to run emop's projects

function check_input() {
  if [[ ! -f ${SHAS_FILE} || -z ${REPO} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage bash vms.sh <SHAS-FILE> <repo> <output-dir>"
    exit 1
  fi
  
  if [[ ! ${SHAS_FILE} =~ ^/.* ]]; then
    SHAS_FILE=${SCRIPT_DIR}/${SHAS_FILE}
  fi
  
  if [[ ! ${OUTPUT_DIR} =~ ^/.* ]]; then
    OUTPUT_DIR=${SCRIPT_DIR}/${OUTPUT_DIR}
  fi
  
  if [[ ! ${REPO_DIR} =~ ^/.* ]]; then
    REPO_DIR=${SCRIPT_DIR}/${REPO_DIR}
  fi
  
  if [[ ! ${LOG_DIR} =~ ^/.* ]]; then
    LOG_DIR=${SCRIPT_DIR}/${LOG_DIR}
  fi

  if [[ ! ${PROJECT_DIR} =~ ^/.* ]]; then
    PROJECT_DIR=${SCRIPT_DIR}/${PROJECT_DIR}
  fi
  
  mkdir -p ${LOG_DIR}
  cp -r ${HOME}/dependencies/repo ${REPO_DIR}
}

function setup() {
  log "Setting up environment..."
  
  # Install JavaMOP agent
  mvn -Dmaven.repo.local=${REPO_DIR} install:install-file -Dfile=${SCRIPT_DIR}/../mop/no-track-agent.jar -DgroupId="javamop-agent" -DartifactId="javamop-agent" -Dversion="1.0" -Dpackaging="jar" &> /dev/null
  
  # Clone project
  pushd ${OUTPUT_DIR}
  git clone https://github.com/${REPO} project
  if [[ $? -ne 0 ]]; then
    log "Cannot clone project"
    exit 1
  fi
  popd
  
  cp ${EXTENSION_PATH}/emop-extension-1.0.jar ${MAVEN_HOME}/lib/ext/
}

function vms() {
  local sha=$1
  local attempt=${2:-1}
  
  mkdir -p ${LOG_DIR}/${sha}
  local filename="${LOG_DIR}/${sha}/vms.log"
  if [[ ${attempt} -ne 1 ]]; then
    filename="${LOG_DIR}/${sha}/vms_${attempt}.log"
  fi
  
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Compiling"
  mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR} ${SKIP} clean test-compile > ${LOG_DIR}/${sha}/compile.log

  log "Running test with VMS"
  uptime >> ${filename}
  
  local start=$(date +%s%3N)
  export ADD_EMOP_EXTENSION=1
  export MOP_AGENT_PATH="-javaagent:\${settings.localRepository}/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar"
  
  (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR} ${SKIP} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar -DjavamopAgent="${REPO_DIR}/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar" -DforceSave=true emop:vms) &>> ${filename}
  status=$?
  local end=$(date +%s%3N)
  duration=$((end - start))
  
  log "Duration: ${duration} ms, status: ${status}" |& tee -a ${filename}
  
  if [[ ${status} -ne 0 ]]; then
    if [[ ${attempt} -ge 3 ]]; then
      log "Unable to run plugin on SHA ${sha} 3 times, exiting..."
      
      echo "${sha},${status},${duration}" >> ${LOG_DIR}/report.csv
      return 1
    else
      log "Unable to run plugin on SHA ${sha}, try again after 60 seconds"
    fi
    
    # Restore project and repo to previous OK state
    cd ${SCRIPT_DIR}
    rm -rf ${PROJECT_DIR} ${REPO_DIR}
    cp -a /tmp/project ${PROJECT_DIR}
    cp -a /tmp/repo ${REPO_DIR}
    sleep 60
    cd ${PROJECT_DIR}
    
    vms ${sha} $((attempt + 1))
    return $?
  else
    mkdir ${LOG_DIR}/${sha}/violations
    move_violations ${LOG_DIR}/${sha}/violations violation-counts true
    
    echo "${sha},0,${duration}" >> ${LOG_DIR}/report.csv
    return 0
  fi
}

function run() {
  pushd ${OUTPUT_DIR}/project &> /dev/null
  for sha in $(cat ${SHAS_FILE}); do
    log "Checking out to ${sha}"
    mkdir -p ${LOG_DIR}/${sha}
    
    # Backup project and repo
    rm -rf /tmp/project /tmp/repo
    cp -a ${PROJECT_DIR} /tmp/project
    cp -a ${REPO_DIR} /tmp/repo
    
    pushd ${OUTPUT_DIR}/project
    git checkout ${sha} &> /dev/null
    treat_special ${REPO} ${sha}
    vms ${sha}
    local status=$?
    popd
    
    if [[ -d ${PROJECT_DIR}/.starts ]]; then
      cp -r ${PROJECT_DIR}/.starts ${LOG_DIR}/${sha}/artifact
      
      pushd ${LOG_DIR}/${sha} &> /dev/null
      tar -I pigz -cf artifact.tar.gz artifact
      if [[ $? -eq 0 && -f artifact.tar.gz ]]; then
        rm -rf artifact
      fi
      popd &> /dev/null
    fi
    
    if [[ ${status} -ne 0 ]]; then
      log "Unable to run plugin with the previous SHA, exiting..."
      exit 1
    fi

    # Delete backup
    rm -rf /tmp/project
    rm -rf /tmp/repo
  done
  popd &> /dev/null
}

export RVMLOGGINGLEVEL=UNIQUE
check_input
setup
run
