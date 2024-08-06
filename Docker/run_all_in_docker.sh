#!/bin/bash
#
# Run run_all.sh in Docker
# Before running this script, run `docker login`
# Usage: run_all_in_docker.sh <projects-list> <sha-dir> <output-dir> [variant=online] [stats=false] [branch=false] [timeout=86400s] [test-timeout=1800s] [threads=20]
# sha directory must contains file: <project-name>.txt
#
SCRIPT_DIR=$(cd $(dirname $0) && pwd)
RTS=""
PROFILE=""
TOOL=""

while getopts :r:p:t: opts; do
  case "${opts}" in
    r ) RTS="${OPTARG}" ;;
    p ) PROFILE="${OPTARG}" ;;
    t ) TOOL="${OPTARG}" ;;
  esac
done
shift $((${OPTIND} - 1))

PROJECTS_LIST=$1
SHA_DIR=$2
OUTPUT_DIR=$3
VARIANT=${4:-"online"}
STATS=${5-"false"}
BRANCH=$6
TIMEOUT=$7
TEST_TIMEOUT=$8
THREADS=$9
OLD_DATA=${10}

shift 10

function check_input() {
  if [[ ! -d ${SHA_DIR} || ! -f ${PROJECTS_LIST} || -z ${OUTPUT_DIR} ]]; then
    echo "Usage: run_all_in_docker.sh <projects-list> <sha-dir> <output-dir> [variant=online] [stats=false] [branch=false] [timeout=86400s] [test-timeout=1800s] [threads=20] [old-data]"
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

  if [[ -z $(grep "###" ${PROJECTS_LIST}) ]]; then
    echo "You must end your projects-list file with ###"
    exit 1
  fi

  if [[ -z ${TIMEOUT} ]]; then
    TIMEOUT=86400s
  fi
}


function run_project() {
  local repo=$1
  shift 1
  local techniques="$@"

  if [[ ${repo} == *","* ]]; then
    techniques=$(echo "${repo}" | cut -d ',' -f 2- | tr ',' ' ')
    repo=$(echo "${repo}" | cut -d ',' -f 1)
  fi

  local project_name=$(echo ${repo} | tr / -)

  if [[ ! -f ${SHA_DIR}/${project_name}.txt ]]; then
    echo "Skip ${project_name} because its sha file is not in sha-directory"
    return
  fi

  local start=$(date +%s%3N)
  echo "Running ${project_name} with techniques ${techniques}"
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
  
  if [[ -n ${THREADS} ]]; then
    echo "Setting threads to ${THREADS}"
    docker exec -w /home/iemop/iemop ${id} sed -i "s/THREADS=.*/THREADS=${THREADS}/" scripts/constants.sh
  fi

  docker cp ${SHA_DIR}/${project_name}.txt ${id}:/home/iemop/sha.txt
  
  if [[ -n ${OLD_DATA} && -d ${OLD_DATA} ]]; then
    docker exec -w /home/iemop ${id} mkdir output
    
    pushd ${OLD_DATA}/${project_name}/output &> /dev/null
    for r in $(ls | grep "repo"); do
      echo "Copying ${OLD_DATA}/${project_name}/output/${r} to docker"
      docker cp ${OLD_DATA}/${project_name}/output/${r} ${id}:/home/iemop/output/${r}
      docker exec --user root ${id} chown -R iemop:iemop /home/iemop/output/${r}
    done
    popd &> /dev/null
  fi

  local arguments=""
  if [[ -n ${RTS} ]]; then
    arguments="-r ${RTS}"
  fi
  if [[ -n ${PROFILE} && ${PROFILE} == "true" ]]; then
    arguments="${arguments} -p /home/iemop/async-profiler-2.9-linux-x64/build/libasyncProfiler.so"
  fi
  if [[ -n ${TOOL} ]]; then
    arguments="${arguments} -t ${TOOL}"
  fi
  
  echo "Run command: timeout ${TIMEOUT} bash scripts/run_all.sh ${arguments} /home/iemop/sha.txt ${repo} /home/iemop/output ${STATS} ${VARIANT} ${techniques}"
  timeout ${TIMEOUT} docker exec -w /home/iemop/iemop -e M2_HOME=/home/iemop/apache-maven -e MAVEN_HOME=/home/iemop/apache-maven -e CLASSPATH=/home/iemop/aspectj-1.9.7/lib/aspectjtools.jar:/home/iemop/aspectj-1.9.7/lib/aspectjrt.jar:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar: -e PATH=/home/iemop/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/home/iemop/aspectj-1.9.7/bin:/home/iemop/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ${id} timeout ${TIMEOUT} bash scripts/run_all.sh ${arguments} /home/iemop/sha.txt ${repo} /home/iemop/output ${STATS} ${VARIANT} ${techniques} &> ${OUTPUT_DIR}/${project_name}/docker.log

  docker cp ${id}:/home/iemop/output ${OUTPUT_DIR}/${project_name}/output

  docker rm -f ${id}
  
  local end=$(date +%s%3N)
  local duration=$((end - start))
  echo "Finished running ${project_name} in ${duration} ms"
}

function run_all() {
  while true; do
    if [[ ! -s ${PROJECTS_LIST} ]]; then
      echo "${PROJECTS_LIST} is empty..."
      exit 0
    fi

    local project=$(head -n 1 ${PROJECTS_LIST})
    if [[ ${project} == "###" ]]; then
      echo "Finished running all projects"
      exit 0
    fi

    if [[ -z $(grep "###" ${PROJECTS_LIST}) ]]; then
      echo "You must end your projects-list file with ###"
      exit 1
    fi

    sed -i 1d ${PROJECTS_LIST}
    echo $project >> ${PROJECTS_LIST}
    run_project ${project} $@
  done
}

check_input
run_all $@
