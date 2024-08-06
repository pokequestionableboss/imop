#!/bin/bash

SCRIPT_DIR=$(cd $(dirname $0) && pwd)
source ${SCRIPT_DIR}/constants.sh
source ${SCRIPT_DIR}/utils.sh

function run_emop() {
  local sha=$1
  local run_variant=$2
  local attempt=${3:-1}
  
  mkdir -p ${LOG_DIR}/${sha}/${run_variant}
  local filename="${LOG_DIR}/${sha}/${run_variant}/test.log"
  if [[ ${attempt} -ne 1 ]]; then
    filename="${LOG_DIR}/${sha}/${run_variant}/test_${attempt}.log"
  fi
  
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Running test with eMOP (${run_variant}, SHA: ${sha}) [attempt: ${attempt}]"
  uptime >> ${filename}
  
  local start=$(date +%s%3N)
  export ADD_EMOP_EXTENSION=1
  export MOP_AGENT_PATH="-javaagent:\${settings.localRepository}/javamop-agent-${run_variant}/javamop-agent-${run_variant}/1.0/javamop-agent-${run_variant}-1.0.jar"
  if [[ -n ${PROFILE} ]]; then
    export MOP_AGENT_PATH="${MOP_AGENT_PATH} -agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
  fi
  
  cp ${EXTENSION_PATH}/emop-extension-1.0.jar ${MAVEN_HOME}/lib/ext/
  if [[ ${run_variant} == "ps1c" ]]; then
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar -DincludeNonAffected=false -DclosureOption=PS1 -DjavamopAgent="${REPO_DIR}-${run_variant}/javamop-agent-ps1c/javamop-agent-ps1c/1.0/javamop-agent-ps1c-1.0.jar" emop:rps) &>> ${filename}
  else
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar -DincludeNonAffected=false -DincludeLibraries=false -DclosureOption=PS3 -DjavamopAgent="${REPO_DIR}-${run_variant}/javamop-agent-ps3cl/javamop-agent-ps3cl/1.0/javamop-agent-ps3cl-1.0.jar" emop:rps) &>> ${filename}
  fi
  status=$?
  rm ${MAVEN_HOME}/lib/ext/emop-extension-1.0.jar
  unset MOP_AGENT_PATH
  unset ADD_EMOP_EXTENSION
  local end=$(date +%s%3N)
  
  duration=$((end - start))
  
  uptime >> ${filename}
  log "Duration: ${duration} ms, status: ${status}" |& tee -a ${filename}
  
  if [[ ${status} -ne 0 ]]; then
    if [[ ${attempt} -ge 3 ]]; then
      log "Unable to run plugin on SHA ${sha} 3 times (variant: ${run_variant}), exiting..."
      
      echo ",${status},${duration}" >> ${LOG_DIR}/report.csv
      return 1
    else
      log "Unable to run plugin on SHA ${sha} (variant: ${run_variant}), try again after 60 seconds"
    fi
    
    # Restore project and repo to previous OK state
    cd ${SCRIPT_DIR}
    rm -rf ${PROJECT_DIR}-${run_variant} ${REPO_DIR}-${run_variant}
    cp -a /tmp/project-${run_variant} ${PROJECT_DIR}-${run_variant}
    cp -a /tmp/repo-${run_variant} ${REPO_DIR}-${run_variant}
    sleep 60
    cd ${PROJECT_DIR}-${run_variant}
    
    run_emop ${sha} ${run_variant} $((attempt + 1))
    return $?
  else
    mkdir ${LOG_DIR}/${sha}/${run_variant}/violations
    move_violations ${LOG_DIR}/${sha}/${run_variant}/violations
    
    if [[ -n ${PROFILE} ]]; then
      if [[ -f profile.jfr ]]; then
        mv profile.jfr ${LOG_DIR}/${sha}/${run_variant}/profile.jfr
      else
        log "SHA ${SHA} variant ${run_variant} is missing profile.jfr!"
      fi
    fi
    
    echo -n ",0,${duration}" >> ${LOG_DIR}/report.csv
    return 0
  fi
}

function run_emop_with_variant() {
  local sha=$1
  local run_variant=$2
  
  # Backup project and repo
  rm -rf /tmp/project-${run_variant} /tmp/repo-${run_variant}
  cp -a ${PROJECT_DIR}-${run_variant} /tmp/project-${run_variant}
  cp -a ${REPO_DIR}-${run_variant} /tmp/repo-${run_variant}
  run_emop ${sha} ${run_variant}
  local status=$?
  
  # Delete backup
  rm -rf /tmp/project-${run_variant}
  rm -rf /tmp/repo-${run_variant}
  
  if [[ -d ${PROJECT_DIR}-${run_variant}/.starts ]]; then
    cp -r ${PROJECT_DIR}-${run_variant}/.starts ${LOG_DIR}/${sha}/${run_variant}/artifact
    
    pushd ${LOG_DIR}/${sha}/${run_variant} &> /dev/null
    tar -I pigz -cf artifact.tar.gz artifact
    if [[ $? -eq 0 && -f artifact.tar.gz ]]; then
      rm -rf artifact
    fi
    popd &> /dev/null
  fi
  
  log "<<< Finished testing SHA ${sha} (variant: ${run_variant})"
  
  if [[ ${status} -ne 0 ]]; then
    log "Unable to run plugin with the previous SHA (variant: ${run_variant}), exiting..."
    exit 1
  fi
}
