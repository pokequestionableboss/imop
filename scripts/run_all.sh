#!/bin/bash
#
# Measure test, mop, emop, and iemop e2e time
# Usage: run_all.sh <SHAS-FILE> <repo> <output-dir> [stats=false] [variant=online]
# SHAS-FILE: oldest sha first, newest sha last
#
SCRIPT_DIR=$(cd $(dirname $0) && pwd)

export RTS=""
export PROFILE=""
export TOOL=""

while getopts :r:p:t: opts; do
  case "${opts}" in
    r ) RTS="${OPTARG}" ;;
    p ) PROFILE="${OPTARG}" ;;
    t ) TOOL="${OPTARG}" ;;
  esac
done
shift $((${OPTIND} - 1))

export SHAS_FILE=$1
export REPO=$2
export PROJECT_NAME=$(echo ${REPO} | tr / -)
export OUTPUT_DIR=$3
export STATS=${4:-false}
export VARIANT=${5:-"online"}

export PROJECT_DIR=${OUTPUT_DIR}/project
export PROJECT_DOWNLOAD_JAR_DIR=${OUTPUT_DIR}/project-prep
export REPO_DIR=${OUTPUT_DIR}/repo
export LOG_DIR=${OUTPUT_DIR}/logs
export EXTENSION_PATH=${SCRIPT_DIR}/../extensions/
export PREFIX="[IMOP]"

export all_variants=("test" "mop" "ps1c" "ps3cl" "mopCache" "mopCacheShared" "online" "incrementalJar" "jarThreads" "cleanByteCode" "storeHashes" "incrementalJarThreads" "cleanByteCodeThreads" "storeHashesThreads" "methodCIA" "classCIA" "dynamicCIA" "methodCIAThreads" "classCIAThreads" "dynamicCIAThreads")
export emop_variants=("ps1c" "ps3cl")
export cia_variants=("methodCIA" "classCIA" "dynamicCIA")
export iemop_variants=()

source ${SCRIPT_DIR}/constants.sh
source ${SCRIPT_DIR}/utils.sh
source ${SCRIPT_DIR}/setup.sh
source ${SCRIPT_DIR}/treat_special.sh # required to run emop's projects
source ${SCRIPT_DIR}/variants/test.sh
source ${SCRIPT_DIR}/variants/iemop.sh
source ${SCRIPT_DIR}/variants/emop.sh

function check_input() {
  if [[ ! -f ${SHAS_FILE} || -z ${REPO} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage bash run_all.sh <SHAS-FILE> <repo> <output-dir> [stats=false] [variant=online]"
    exit 1
  fi
  
  if [[ ${VARIANT} != "test" && ${VARIANT} != "mop" && ${VARIANT} != "ps1c" && ${VARIANT} != "ps3cl" && ${VARIANT} != "mopCache" && ${VARIANT} != "mopCacheShared" && ${VARIANT} != "online" && ${VARIANT} != "incrementalJar" && ${VARIANT} != "jarThreads" && ${VARIANT} != "cleanByteCode" && ${VARIANT} != "storeHashes" && ${VARIANT} != "incrementalJarThreads" && ${VARIANT} != "cleanByteCodeThreads" && ${VARIANT} != "storeHashesThreads" && ${VARIANT} != "methodCIA" && ${VARIANT} != "classCIA" && ${VARIANT} != "dynamicCIA" &&  ${VARIANT} != "methodCIAThreads" && ${VARIANT} != "classCIAThreads" && ${VARIANT} != "dynamicCIAThreads" &&  ${VARIANT} != "dynamicCIALazy" && ${VARIANT} != "dynamicCIALazyThreads" && ${VARIANT} != "cia" && ${VARIANT} != "emop" && ${VARIANT} != "iemop" && ${VARIANT} != "all" ]]; then
    echo "Invalid variant: test, mop, ps1c, ps3cl, mopCache, mopCacheShared, online, incrementalJar, jarThreads, cleanByteCode, storeHashes, incrementalJarThreads, cleanByteCodeThreads, storeHashesThreads, methodCIA, classCIA, dynamicCIA, methodCIAThreads, classCIAThreads, dynamicCIAThreads, dynamicCIALazy, dynamicCIALazyThreads, cia, emop, iemop, or all"
    exit 1
  fi
  
  if [[ ! ${SHAS_FILE} =~ ^/.* ]]; then
    SHAS_FILE=${SCRIPT_DIR}/${SHAS_FILE}
  fi
  
  if [[ ! ${OUTPUT_DIR} =~ ^/.* ]]; then
    OUTPUT_DIR=${SCRIPT_DIR}/${OUTPUT_DIR}
  fi
  
  if [[ ${VARIANT} == "iemop" ]]; then
    shift 5
    for run_variant in "$@"; do
      iemop_variants+=("${run_variant}")
    done
    
    if [ ${#iemop_variants[@]} -eq 0 ]; then
      echo "Need to list out all variants after iemop"
      exit 1
    fi
  fi
  
  mkdir -p ${LOG_DIR}
  
  echo "SHA: ${SHAS_FILE}"
  echo "REPO: ${REPO}"
  echo "OUTPUT_DIR: ${OUTPUT_DIR}"
  echo "STATS: ${STATS}"  
  if [[ ${VARIANT} == "iemop" ]]; then
    echo -n "VARIANT:"
    for run_variant in ${iemop_variants[@]}; do
      echo -n " ${run_variant}"
    done
    echo ""
  else
    echo "VARIANT: ${VARIANT}"
  fi
  if [[ -n ${RTS} ]]; then
    echo "RTS: ${RTS}"
  else
    echo "RTS: disabled"
  fi
}

function download_jar() {
  local sha=$1
  local attempt=${2:-1}
  
  mkdir -p ${TMP_DIR} && chmod -R +w ${TMP_DIR} && rm -rf ${TMP_DIR} && mkdir -p ${TMP_DIR}
  
  log "Downloading jar by running test (SHA: ${sha}) [attempt: ${attempt}]"
  local start=$(date +%s%3N)
  (time timeout ${TIMEOUT} mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR} ${SKIP} clean test) &>> ${LOG_DIR}/${sha}/download_jar_${attempt}.log
  local status=$?
  local end=$(date +%s%3N)
  local duration=$((end - start))
  
  if [[ ${status} -ne 0 ]]; then
    if [[ ${STATS} == "false" || -z $(grep "maven-surefire-plugin:3.1.2" ${LOG_DIR}/${sha}/download_jar_${attempt}.log) ]]; then
      # If stats is true and maven-surefire-plugin:3.1.2 is in the log, that means the project tried to use the instrumented surefire-booter
      # we can safely ignore this error
      if [[ ${attempt} -ge 5 ]]; then
        log "Unable to download jar for SHA ${sha} 5 times, exiting..."
        echo "${sha},${status},${duration},-1,-1" >> ${LOG_DIR}/report.csv
        exit 1
      else
        log "Unable to download jar for SHA ${sha}, try again after 60 seconds"
      fi
      
      sleep 60
      download_jar ${sha} $((attempt + 1))
      return 0
    fi
  fi
  # List dependencies for debug purposes (not timed)
  log "Getting debug information (SHA: ${sha})"
  
  mvn -Dmaven.repo.local=${REPO_DIR} dependency:tree -DoutputFile=${LOG_DIR}/${sha}/dependency-tree.log &> /dev/null
  local tree_status=$?
  mvn -Dmaven.repo.local=${REPO_DIR} dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q 2>&1 | cat > ${LOG_DIR}/${sha}/classpath.log
  local cp_status=$?
  if [[ ${tree_status} -ne 0 || ${cp_status} -ne 0 ]]; then
    log "Unable to get dependency tree or classpath"
  fi
  
  if [[ ${VARIANT} == "all" ]]; then
    for run_variant in ${all_variants[@]}; do
      cp -rn ${REPO_DIR}/* ${REPO_DIR}-${run_variant}
    done
  elif [[ ${VARIANT} == "cia" ]]; then
    for run_variant in ${cia_variants[@]}; do
      cp -rn ${REPO_DIR}/* ${REPO_DIR}-${run_variant}
    done
  elif [[ ${VARIANT} == "emop" ]]; then
    for run_variant in ${emop_variants[@]}; do
      cp -rn ${REPO_DIR}/* ${REPO_DIR}-${run_variant}
    done
  elif [[ ${VARIANT} == "iemop" ]]; then
    for run_variant in ${iemop_variants[@]}; do
      cp -rn ${REPO_DIR}/* ${REPO_DIR}-${run_variant}
    done
  else
    cp -rn ${REPO_DIR}/* ${REPO_DIR}-${VARIANT}
  fi
  
  echo -n "${sha},${status},${duration}" >> ${LOG_DIR}/report.csv
}

function run_with_variant() {
  local sha=$1
  local run_variant=$2
  
  mkdir -p ${LOG_DIR}/${sha}/${run_variant}
  log "Compiling (${run_variant}, SHA: ${sha})"
  (time timeout ${TIMEOUT} mvn -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} clean test-compile) &> ${LOG_DIR}/${sha}/${run_variant}/compile.log
  if [[ $? -ne 0 ]]; then
    log "Unable to compile"
    exit 1
  fi
  
  if [[ ${run_variant} == "test" || ${run_variant} == "mop" || ${run_variant} == "mopCache" || ${run_variant} == "mopCacheShared" ]]; then
    run_test_with_variant ${sha} ${run_variant}
  elif [[ ${run_variant} == "ps1c" || ${run_variant} == "ps3cl" ]]; then
    run_emop_with_variant ${sha} ${run_variant}
  else
    run_iemop_with_variant ${sha} ${run_variant}
  fi
}

function measure_time() {
  cd ${OUTPUT_DIR}
  local last_sha=""
  for sha in $(cat ${SHAS_FILE}); do
    log ">>> Testing SHA ${sha}"
    mkdir -p ${LOG_DIR}/${sha}
    last_sha=${sha}
    
    pushd ${PROJECT_DOWNLOAD_JAR_DIR} &> /dev/null
    # Move to project-prep, run test to download jar
    git checkout ${sha} &> /dev/null
    treat_special ${REPO} ${sha}
    download_jar ${sha}
    popd &> /dev/null
  
    if [[ ${VARIANT} == "all" ]]; then
      for run_variant in ${all_variants[@]}; do
        cd ${PROJECT_DIR}-${run_variant}
        git checkout ${sha} &> /dev/null
        treat_special ${REPO} ${sha}
        run_with_variant ${sha} ${run_variant}
      done
    elif [[ ${VARIANT} == "cia" ]]; then
      for run_variant in ${cia_variants[@]}; do
        cd ${PROJECT_DIR}-${run_variant}
        git checkout ${sha} &> /dev/null
        treat_special ${REPO} ${sha}
        run_with_variant ${sha} ${run_variant}
      done
    elif [[ ${VARIANT} == "emop" ]]; then
      for run_variant in ${emop_variants[@]}; do
        cd ${PROJECT_DIR}-${run_variant}
        git checkout ${sha} &> /dev/null
        treat_special ${REPO} ${sha}
        run_with_variant ${sha} ${run_variant}
      done
    elif [[ ${VARIANT} == "iemop" ]]; then
      for run_variant in ${iemop_variants[@]}; do
        cd ${PROJECT_DIR}-${run_variant}
        git checkout ${sha} &> /dev/null
        treat_special ${REPO} ${sha}
        run_with_variant ${sha} ${run_variant}
      done
    else
      cd ${PROJECT_DIR}-${VARIANT}
      git checkout ${sha} &> /dev/null
      treat_special ${REPO} ${sha}
      run_with_variant ${sha} ${VARIANT}
    fi
    echo "" >> ${LOG_DIR}/report.csv
  done
  
  if [[ -n ${last_sha} ]]; then
    backup_final_run_artifact ${last_sha}
  fi
}

export RVMLOGGINGLEVEL=UNIQUE
check_input $@
setup
measure_time
