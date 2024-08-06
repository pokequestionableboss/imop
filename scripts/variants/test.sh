#!/bin/bash

SCRIPT_DIR=$(cd $(dirname $0) && pwd)
source ${SCRIPT_DIR}/constants.sh
source ${SCRIPT_DIR}/utils.sh

function run_test() {
  local sha=$1
  local run_variant=$2
  local attempt=${3:-1}
  
  mkdir -p ${LOG_DIR}/${sha}/${run_variant}
  local filename="${LOG_DIR}/${sha}/${run_variant}/test.log"
  if [[ ${attempt} -ne 1 ]]; then
    filename="${LOG_DIR}/${sha}/${run_variant}/test_${attempt}.log"
  fi
  
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Running ${run_variant} with (${run_variant}, SHA: ${sha}) [attempt: ${attempt}]"
  uptime >> ${filename}
  
  local goal="test"
  if [[ ${RTS} == "starts" ]]; then
    export SUREFIRE_VERSION="3.1.2"
    goal="edu.illinois:starts-maven-plugin:1.4:starts"
  elif [[ ${RTS} == "ekstazi" ]]; then
    export SUREFIRE_VERSION="2.14"
    goal="org.ekstazi:ekstazi-maven-plugin:5.3.0:ekstazi"
  fi
  
  local start=$(date +%s%3N)
  if [[ ${run_variant} == "mop" ]]; then
    if [[ -n ${PROFILE} ]]; then
      export MOP_AGENT_PATH="-javaagent:\${settings.localRepository}/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar -agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    fi
    
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar ${goal}) &>> ${filename}
    status=$?
  elif [[ ${run_variant} == "mopCache" ]]; then
    export MOP_AGENT_PATH="-javaagent:\${settings.localRepository}/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar -Daj.weaving.cache.enabled=true -Daj.weaving.cache.dir=/tmp/aspectj-cache/"
    if [[ -n ${PROFILE} ]]; then
      export MOP_AGENT_PATH="${MOP_AGENT_PATH} -agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    fi
    
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar ${goal}) &>> ${filename}
    status=0 # allow error
  elif [[ ${run_variant} == "mopCacheShared" ]]; then
    export MOP_AGENT_PATH="-javaagent:\${settings.localRepository}/javamop-agent/javamop-agent/1.0/javamop-agent-1.0.jar -Daj.weaving.cache.enabled=true -Daj.weaving.cache.dir=/tmp/aspectj-cache-shared/ -Daj.weaving.cache.impl=shared"
    if [[ -n ${PROFILE} ]]; then
      export MOP_AGENT_PATH="${MOP_AGENT_PATH} -agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    fi
    
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} -Dmaven.ext.class.path=${EXTENSION_PATH}/javamop-extension-1.0.jar ${goal}) &>> ${filename}
    status=$?
  else
    local more_argument=""
    if [[ -n ${PROFILE} ]]; then
      more_argument="-DargLine=-agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    fi
    
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} ${goal}) &>> ${filename}
    status=$?
  fi
  
  local end=$(date +%s%3N)
  duration=$((end - start))
  
  uptime >> ${filename}
  log "Duration: ${duration} ms, status: ${status}" |& tee -a ${filename}
  
  unset MOP_AGENT_PATH
  unset SUREFIRE_VERSION
  
  if [[ ${status} -ne 0 ]]; then
    if [[ ${attempt} -ge 3 ]]; then
      log "Unable to run test on SHA ${sha} 3 times (variant: ${run_variant}), exiting..."
      
      echo ",${status},${duration}" >> ${LOG_DIR}/report.csv
      return 1
    else
      log "Unable to run test on SHA ${sha} (variant: ${run_variant}), try again after 60 seconds"
    fi
    
    # Restore project and repo to previous OK state
    cd ${SCRIPT_DIR}
    rm -rf ${PROJECT_DIR}-${run_variant} ${REPO_DIR}-${run_variant}
    cp -a /tmp/project-${run_variant} ${PROJECT_DIR}-${run_variant}
    cp -a /tmp/repo-${run_variant} ${REPO_DIR}-${run_variant}
    sleep 60
    cd ${PROJECT_DIR}-${run_variant}
    
    run_test ${sha} ${run_variant} $((attempt + 1))
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

function run_test_with_variant() {
  local sha=$1
  local run_variant=$2
  
  # Backup project and repo
  rm -rf /tmp/project-${run_variant} /tmp/repo-${run_variant}
  cp -a ${PROJECT_DIR}-${run_variant} /tmp/project-${run_variant}
  cp -a ${REPO_DIR}-${run_variant} /tmp/repo-${run_variant}
  run_test ${sha} ${run_variant}
  local status=$?
  
  # Delete backup
  rm -rf /tmp/project-${run_variant}
  rm -rf /tmp/repo-${run_variant}

  if [[ -d ${PROJECT_DIR}-${run_variant}/.starts ]]; then
    cp -r ${PROJECT_DIR}-${run_variant}/.starts ${LOG_DIR}/${sha}/${run_variant}/rts-artifact
  fi

  log "<<< Finished testing SHA ${sha} (variant: ${run_variant})"
  
  if [[ ${status} -ne 0 ]]; then
    log "Unable to run test with the previous SHA (variant: ${run_variant}), exiting..."
    exit 1
  fi
}
