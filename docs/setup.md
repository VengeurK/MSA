 # Setup

 ## Directory structure

 Create a folder named `forge` and a folder `mod` inside. Then clone this repository inside of `/forge/mod/src`.

`git clone https://github.com/VengeurK/MSA.git src`

 Confirm that `src` contains `setup.sh`.

 ## Framework Dependencies

 * Java Development Kit 8
 * Python 3
    * numpy
    * gym
    * baselines
 * (UNIX only) unzip command

## Agent Scripts Dependencies

* Python 3
    * tensorflow
    * [baselines (fork)](https://github.com/VengeurK/Villagers-Baselines-Fork)
    * pytorch 0.4
    * [pytorch reinforcement learning (fork)](https://github.com/VengeurK/pytorch-a2c-ppo-acktr)
    * matplotlib
    * plotly
    * h5py
    * opencv-python
    * visdom

## Running the setup script

To avoid issues, make sure that `forge` only contains `mod` only containing `src`.

### Unix

Open a terminal in `forge/mod/src/` and execute `./setup.sh`.

### Windows

Open a `PowerShell` in `forge\mod\src\` and execute `.\setup.ps1`.

## Starting the Minecraft Server

Run `./gradlew runServer -Pusername=` in `forge/mod/`.


## Starting a Minecraft Client

Run `./gradlew runClient -Pusername=<name>` in `forge/mod/` having replaced `<name>` with your desired username.

## Running agent scripts

Scripts should be placed in `forge/mod/src/main/python/`. They should be run from the `forge/mod/run` folder using the symbolic link to the `python` folder.

** Exemples to run in `forge/mod/run/`: **

* `python python/agent_dummy.py FloorSurvival`
* `python python/agent_random.py FloorSurvival`
* `python python/agent_tree.py Pattern`
* `python python/multi_single_env_agent.py 4 Pattern "python python/agent_tree.py"`
* `python python/agent_ppo.py CowArena[5,2] --epochs 8 --actorbatch_timesteps 8004 --learning_rate 5e-5 --total_timesteps 200000`
* `python python/agent_acktr.py ParkourRandom`
* `python python/agent_rl.py --env-name mc.ParkourRandom --num-processes 16 --num-stack 1 --num-steps 50 --algo ppo --num-frames 200000`
* `python python/agent_rl.py --env-name mc.FloorSurvival --num-processes 16 --num-stack 1 --num-steps 50 --algo ppo --num-frames 200000`
* `python python/multi_agent_test_pattern.py 250`
* `python python/agent_imitation_train.py <dataset> --eval 1 --decay 1e-4 --lr 1e-4 --gpu 0 --mem_std 0` replace `<dataset>` by the name used in the `compile` command.
* `python python/multi_single_env_agent.py 4 Pattern "python python/agent_imitation_enjoy.py tmp/models/imitation_0_epoch_latest --gpu 0"` tests the imitation policy within Minecraft
