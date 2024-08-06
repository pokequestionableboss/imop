# Instrumentation-Driven Evolution-Aware Runtime Verification

## Appendix
See [appendix.pdf](appendix.pdf)

## Projects and data
You can find our 48 projects [here](data/subjects-repo.txt), and you can find their SHAs in this [directory](data/sha).

## Repository structure
| Directory               | Purpose                                    |
| ------------------------| ------------------------------------------ |
| Docker                  | scripts to run our experiments in Docker   |
| data                    | raw data (see section above)               |
| extensions              | a collection of Maven extensions           |
| maven-plugin            | our iMOP maven plugin                      |
| mop                     | a collection of JavaMOP specifications     |
| scripts                 | our experimental infrastructure            |

## Usage
### Prerequisites:
- A x86-64 architecture machine
- Ubuntu 20.04
- [Docker](https://docs.docker.com/get-docker/)
### Setup
First, you need to build a Docker image. Run the following commands in terminal.
```sh
docker build -f Docker/Dockerfile . --tag=imop:latest
docker run -it --rm imop:latest
./setup.sh  # run this command in Docker container
```
Then, run the following command in a new terminal window.
```sh
docker ps  # get container id
docker commit <container-id> imop:latest
# You can now close the previous terminal window by entering `exit`
```

### Run experiments
```sh
# Enter the following commands outside the Docker container and inside the current repository directory
cd Docker

# Run one of the following
# 1) if you want to run iMOP on all projects (could take multiple days)
cp projects-all.txt projects.txt
# 2) if you want to run iMOP on one project (should take less than 3 hours)
cp projects-one.txt projects.txt

bash run_all_in_docker.sh projects.txt ../data/sha output all false false 259200s 10800s 20 false
```
#### Output
The file in `output/logs/report.csv` contains the following csv (times are in ms):
```
sha, [ignore], [ignore], [ignore], test time, [ignore], mop time, [ignore], ps1c time, [ignore], ps3cl time, [ignore], ajc^{def} time, [ignore], ajc^{one} time, [ignore], UJ^s_o time, [ignore], UC^s_o time, [ignore], UJ^p_o time, [ignore], UB^s_o time, [ignore], UB^s_h time, [ignore], UC^p_o time, [ignore], UB^p_o time, [ignore], UB^p_h time, [ignore], [ignore], [ignore], AB^{ss}_h time, [ignore], AB^{sd}_h time, [ignore], [ignore], [ignore], AB^{ps}_h time, [ignore], AB^{pd}_h time
...
```

### Experimental techniques that trade safety for speed (not among the 14 official iMOP techniques)
```sh
# Enter the following commands outside the Docker container and inside the current repository directory
cd Docker

# Run one of the following
# 1) if you want to run iMOP on all projects (could take multiple days)
cp projects-all.txt projects.txt
# 2) if you want to run iMOP on one project (should take less than 3 hours)
cp projects-one.txt projects.txt

# Replace [technique] with one the below
# methodCIA - uses method dependency graph instead of type dependency graph, re-instruments in sequence
# methodCIAThreads - uses method dependency graph instead of type dependency graph, re-instruments in parallel
# dynamicCIALazy - only finds used classes when the classpath has changed between revisions, re-instruments in sequence
# dynamicCIALazyThreads - only finds used classes when the classpath has changed between revisions, re-instruments in parallel
bash run_all_in_docker.sh projects.txt ../data/sha output [technique] false false 259200s 10800s 20 false

# Get total time in ms
tail -n +2 output/logs/report.csv | cut -d ',' -f 5 | paste -sd+ | bc -l
```
