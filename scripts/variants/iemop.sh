#!/bin/bash

SCRIPT_DIR=$(cd $(dirname $0) && pwd)
source ${SCRIPT_DIR}/constants.sh
source ${SCRIPT_DIR}/utils.sh

function run_iemop() {
  local sha=$1
  local run_variant=$2
  local attempt=${3:-1}
  
  mkdir -p ${LOG_DIR}/${sha}/${run_variant}
  local filename="${LOG_DIR}/${sha}/${run_variant}/test.log"
  if [[ ${attempt} -ne 1 ]]; then
    filename="${LOG_DIR}/${sha}/${run_variant}/test_${attempt}.log"
  fi
  
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Running test with IEMOP (${run_variant}, SHA: ${sha}) [attempt: ${attempt}]"
  uptime >> ${filename}
  
  local more_argument=""
  local goal="run"
  if [[ ${RTS} == "starts" ]]; then
    export SUREFIRE_VERSION="3.1.2"
    more_argument="-Dtool=starts"
    goal="rts"
    log "GOAL: ${goal} flag ${more_argument}"
  elif [[ ${RTS} == "ekstazi" ]]; then
    export SUREFIRE_VERSION="2.14"
    more_argument="-Dtool=ekstazi"
    goal="rts"
    log "GOAL: ${goal} flag ${more_argument}"
  fi
  
  if [[ -n ${PROFILE} ]]; then
    if [[ -n ${more_argument} ]]; then
      more_argument="${more_argument} -DargLine=-agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    else
      more_argument="-DargLine=-agentpath:${PROFILE}=start,interval=5ms,event=wall,file=profile.jfr"
    fi
  fi
  
  if [[ -n ${TOOL} ]]; then
    if [[ -n ${more_argument} ]]; then
      more_argument="${more_argument} -Dbism"
    else
      more_argument="-Dbism"
    fi
  fi

  local thread=${THREADS}
  local technique=${run_variant}
  if [[ ${run_variant} == *"@"* ]]; then
    # custom threads
    technique=$(echo ${run_variant} | cut -d '@' -f 1)
    thread=$(echo ${run_variant} | cut -d '@' -f 2)
    log "${run_variant} is ${technique} with ${thread} threads"
  fi
  
  if [[ ${technique} == "bism" ]]; then
    technique="online"
    if [[ -d .iemop ]]; then
      # Non-incremental mode, delete all except resources
      mv .iemop/resources .iemop-resources && rm -rf .iemop && mkdir .iemop && mv .iemop-resources .iemop/resources
    fi
  fi

  if [[ ${run_variant} == "dynamicCIA"* && ${run_variant} != "dynamicCIALazy"* ]]; then
    local start=$(date +%s%3N)
    local more_argument=""
    local duration=0
    local status=0
    
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} -DargLine="-verbose:class" test) &> ${LOG_DIR}/${sha}/${run_variant}/dynamic_classes.log
    if [[ $? -ne 0 ]]; then
      log "Unable to get loaded classes on SHA ${sha} (variant: ${run_variant})"
      status=1
      mv ${LOG_DIR}/${sha}/${run_variant}/dynamic_classes.log ${LOG_DIR}/${sha}/${run_variant}/dynamic_classes_${attempt}.log
    fi

    if [[ ${status} == 0 ]]; then
      if [[ ${attempt} -ne 1 ]]; then
        log "Add skipCleanProject option because the previous the previous byte code cleaning run failed"
        more_argument="-DskipCleanProject=true"
      fi
  
      export ADD_IEMOP_EXTENSION=1
      mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
      (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} -Dmaven.ext.class.path=${EXTENSION_PATH}/iemop-maven-extension-1.0.jar -Dthreads=${thread} -Dstats=${STATS} -Dvariant=${technique} -DdynamicCIA=${LOG_DIR}/${sha}/${run_variant}/dynamic_classes.log -Dtimer=true org.iemop:iemop-maven-plugin:1.0:${goal}) &>> ${filename}
      status=$?
    fi
    
    local end=$(date +%s%3N)
    duration=$((end - start))
  elif [[ ${run_variant} == "dynamicCIALazy"* ]]; then
    local start=$(date +%s%3N)
    
    export ADD_IEMOP_EXTENSION=1
    # DynamicCIA requires setting -DdynamicCIA to the current output log, and setting -DargLine=-verbose:class
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} -Dmaven.ext.class.path=${EXTENSION_PATH}/iemop-maven-extension-1.0.jar -Dthreads=${thread} -Dstats=${STATS} -Dvariant=${technique} -Dtimer=true -DdynamicCIA=${filename} -DargLine=-verbose:class org.iemop:iemop-maven-plugin:1.0:${goal}) &>> ${filename}
    status=$?
  
    local end=$(date +%s%3N)
    duration=$((end - start))
    
    if [[ -f .iemop/dynamic.log ]]; then
      cp .iemop/dynamic.log ${LOG_DIR}/${sha}/${run_variant}/dynamic_classes.log
    fi
    if [[ -f .iemop/dynamic.log.loaded ]]; then
      cp .iemop/dynamic.log.loaded ${LOG_DIR}/${sha}/${run_variant}/dynamic_classes-loaded.log
    fi
  else
    local start=$(date +%s%3N)
    if [[ ${run_variant} == "cleanByteCode"* || ${run_variant} == "storeHashes"* || ${run_variant} == "methodCIA"* || ${run_variant} == "classCIA"* || ${run_variant} == "dynamicCIA"* ]]; then
      local more_argument=""
      if [[ ${attempt} -ne 1 ]]; then
        log "Add skipCleanProject option because the previous the previous byte code cleaning run failed"
        if [[ -n ${more_argument} ]]; then
          more_argument="${more_argument} -DskipCleanProject=true"
        else
          more_argument="-DskipCleanProject=true"
        fi
      fi
    fi
    
    export ADD_IEMOP_EXTENSION=1
    (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} ${more_argument} -Dmaven.ext.class.path=${EXTENSION_PATH}/iemop-maven-extension-1.0.jar -Dthreads=${thread} -Dstats=${STATS} -Dvariant=${technique} -Dtimer=true org.iemop:iemop-maven-plugin:1.0:${goal}) &>> ${filename}
    status=$?
    
    local end=$(date +%s%3N)
    duration=$((end - start))
  fi
  
  unset ADD_IEMOP_EXTENSION
  unset SUREFIRE_VERSION
  
  if [[ -n $(grep --text "Nothing new to instrument or monitor" ${filename}) ]]; then
    log "Nothing new to instrument or monitor, set status to 0"
    echo "Nothing new to instrument or monitor, set status to 0" >> ${filename}
    status=0
  fi

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
    
    run_iemop ${sha} ${run_variant} $((attempt + 1))
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

function run_iemop_with_variant() {
  local sha=$1
  local run_variant=$2
  
  # Backup project and repo
  rm -rf /tmp/project-${run_variant} /tmp/repo-${run_variant}
  cp -a ${PROJECT_DIR}-${run_variant} /tmp/project-${run_variant}
  cp -a ${REPO_DIR}-${run_variant} /tmp/repo-${run_variant}
  run_iemop ${sha} ${run_variant}
  local status=$?
  
  # Delete backup
  rm -rf /tmp/project-${run_variant}
  rm -rf /tmp/repo-${run_variant}
  
  if [[ -d ${PROJECT_DIR}-${run_variant}/.iemop && ${status} -ne 0 ]]; then
    # It is WAY TOOOOO LARGE to save artifact for all run, so we will only save it when there is an error
    backup_artifact ${sha} ${run_variant}
  fi
  
  if [[ -d ${PROJECT_DIR}-${run_variant}/.starts ]]; then
    cp -r ${PROJECT_DIR}-${run_variant}/.starts ${LOG_DIR}/${sha}/${run_variant}/rts-artifact
  fi
  
  log "<<< Finished testing SHA ${sha} (variant: ${run_variant})"
  
  if [[ ${status} -ne 0 ]]; then
    log "Unable to run plugin with the previous SHA (variant: ${run_variant}), exiting..."
    exit 1
  fi
}

function backup_artifact() {
  local sha=$1
  local run_variant=$2

  if [[ -d ${PROJECT_DIR}-${run_variant}/.iemop ]]; then
    log "Backing up ${sha} - ${run_variant}'s artifact"
    
    mkdir -p ${LOG_DIR}/${sha}/${run_variant}
    cp -r ${PROJECT_DIR}-${run_variant}/.iemop ${LOG_DIR}/${sha}/${run_variant}/artifact
    cp -r ${PROJECT_DIR}-${run_variant}/target ${LOG_DIR}/${sha}/${run_variant}/target
    
    pushd ${LOG_DIR}/${sha}/${run_variant} &> /dev/null
    tar -I pigz -cf artifact.tar.gz artifact
    if [[ $? -eq 0 && -f artifact.tar.gz ]]; then
      rm -rf artifact
    fi
    
    tar -I pigz -cf target.tar.gz target
    if [[ $? -eq 0 && -f target.tar.gz ]]; then
      rm -rf target
    fi
    popd &> /dev/null
  fi
}

function backup_final_run_artifact() {
  local last_sha=$1
  
  log "Last SHA - ${last_sha} - backup artifacts"
  
  if [[ ${VARIANT} == "all" ]]; then
    for run_variant in ${all_variants[@]}; do
      backup_artifact ${last_sha} ${run_variant}
    done
  elif [[ ${VARIANT} == "cia" ]]; then
    for run_variant in ${cia_variants[@]}; do
      backup_artifact ${last_sha} ${run_variant}
    done
  elif [[ ${VARIANT} == "emop" ]]; then
    for run_variant in ${emop_variants[@]}; do
      backup_artifact ${last_sha} ${run_variant}
    done
  elif [[ ${VARIANT} == "iemop" ]]; then
    for run_variant in ${iemop_variants[@]}; do
      backup_artifact ${last_sha} ${run_variant}
    done
  else
    backup_artifact ${last_sha} ${VARIANT}
  fi
}
