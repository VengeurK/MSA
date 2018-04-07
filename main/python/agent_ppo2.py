from minecraft_environment import *

def train(num_timesteps, seed):
    env = MinecraftEnv()

    from baselines.common import tf_util as U
    from baselines.ppo1 import mlp_policy, pposgd_simple

    U.make_session(num_cpu=1).__enter__()
    def policy_fn(name, ob_space, ac_space):
        return mlp_policy.MlpPolicy(name=name, ob_space=ob_space, ac_space=ac_space,
            hid_size=64, num_hid_layers=2)

    pposgd_simple.learn(env, policy_fn,
            max_timesteps=num_timesteps,
            timesteps_per_actorbatch=1000,
            clip_param=0.2, entcoeff=0.01,
            optim_epochs=8, optim_stepsize=1e-3, optim_batchsize=64,
            gamma=0.99, lam=0.95, schedule='constant',
        )
    env.close()

def main():
    train(num_timesteps=10 ** 8, seed=42)

if __name__ == '__main__':
    main()