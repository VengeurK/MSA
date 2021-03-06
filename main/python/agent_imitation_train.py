import torch as th
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
import torch.utils.data
import numpy as np
import h5py
import plotly.plotly as py
import plotly.graph_objs as go
import single_env_agent
import minecraft.environment
from collections import deque
from time import time
import concurrent.futures
import traceback
import copy
from rl.utils import AddBias
from rl.distributions import FixedNormal
import visdom
import argparse
import random

def size(dim, ratio):
    channels = 7
    w = int(np.sqrt(dim * ratio / channels))
    h = w // ratio
    return w, h, channels

class Flatten(nn.Module):
    def forward(self, x):
        return x.view(x.size(0), -1)

class DiagGaussian(nn.Module):
    def __init__(self, dim):
        super(DiagGaussian, self).__init__()

        self.logstd = AddBias(torch.zeros(dim))

    def forward(self, x):
        zeros = torch.zeros(x.size())
        if x.is_cuda:
            zeros = zeros.cuda()

        action_logstd = self.logstd(zeros) - 3
        return FixedNormal(x, action_logstd.exp())

class ConstantOutput(nn.Module):
    def __init__(self):
        super(ConstantOutput, self).__init__()
        self.c = nn.Parameter(th.zeros(6))

    def forward(self, obs):
        return self.c

class Policy(nn.Module):
    def __init__(self, obs_dim_w, obs_dim_h, obs_dim_c, act_dim, memory_std):
        super(Policy, self).__init__()
        self.obs_dim_w = obs_dim_w
        self.obs_dim_h = obs_dim_h
        self.obs_dim_c = obs_dim_c

        def init_(m):
            if hasattr(m, 'weight'):
                nn.init.normal_(m.weight)#nn.init.xavier_normal_(m.weight)
            return m
        C = 16
        vision_features = (C * 2) * 2 * 4
        self.memory_features = 256
        self.memory_std = memory_std

        self.vision = nn.Sequential(
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(obs_dim_c, C, 3, groups=1)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C, C, 3, groups=C)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C, C, 3, groups=C)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C, C, 3, groups=C)),
            nn.Tanh(),
            nn.MaxPool2d(2),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C, C * 2, 3, groups=1)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C * 2, C * 2, 3, groups=C*2)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C * 2, C * 2, 3, groups=C*2)),
            nn.Tanh(),
            nn.ReflectionPad2d(1),
            init_(nn.Conv2d(C * 2, C * 2, 3, groups=C*2)),
            nn.Tanh(),
            nn.MaxPool2d((3, 3)),
            Flatten(),
        ).cuda()

        self.memory_layers = 2
        self.memory = nn.LSTM(vision_features, self.memory_features, num_layers=self.memory_layers).cuda()

        #self.memory = nn.Sequential(
        #    nn.Linear(vision_features, self.memory_features),
        #    nn.Tanh()
        #).cuda()

        self.action = nn.Sequential(
            nn.Linear(self.memory_features, 64),
            nn.Tanh(),
            nn.Linear(64, 64),
            nn.Tanh(),
            nn.Linear(64, 64),
            nn.Tanh(),
            nn.Linear(64, act_dim)
        ).cuda()

        self.critic = nn.Sequential(
            nn.Linear(self.memory_features, 1)
        ).cuda()

        self.dist = DiagGaussian(act_dim).cuda()

        self.train()

        self.state_size = 2 * self.memory_layers * self.memory_features

    def _adapt_inputs(self, inputs):
        if len(inputs.shape) < 4:
            if not hasattr(self, 'obs_dim_w'):
                self.obs_dim_w, self.obs_dim_h, self.obs_dim_c = 24, 12, 7
            inputs = inputs.view(-1, self.obs_dim_h, self.obs_dim_w, self.obs_dim_c).permute(0, 3, 1, 2)
        return inputs

    def _value_action(self, inputs, states=None, masks=None):
        if states is None:
            states = th.tanh(th.randn((2, self.memory_layers, 1, self.memory_features)).cuda() * self.memory_std)
            batch_size = 1
        else:
            states = states.view(-1, 2, self.memory_layers, self.memory_features).permute(1, 2, 0, 3).contiguous()
            batch_size = states.size(2)
        inputs = self._adapt_inputs(inputs)
        features = self.vision(inputs)

        self.memory.flatten_parameters()
        features, states = self.memory(features.view(-1, batch_size, features.size(1)), states)
        #features = self.memory(features)
        features = features.view(-1, features.size(2))
        states = th.cat(states).view(2, self.memory_layers, batch_size, self.memory_features).permute(2, 0, 1, 3)
        return self.critic(features), self.action(features), states.contiguous().view(batch_size, -1)

    def act(self, inputs, states=None, masks=None, deterministic=False):
        value, actor_features, states = self._value_action(inputs, states, masks)
        dist = self.dist(actor_features)

        action = actor_features if deterministic else dist.rsample()

        action_log_probs = dist.log_probs(action)
        dist_entropy = dist.entropy().mean()

        return value, action, action_log_probs, states

    def get_value(self, inputs, states=None, masks=None):
        value, _, _ = self._value_action(inputs, states, masks)
        return value

    def evaluate_actions(self, inputs, states, masks, action):
        value, actor_features, states = self._value_action(inputs, states, masks)
        dist = self.dist(actor_features)

        action_log_probs = dist.log_probs(action)
        dist_entropy = dist.entropy().mean()

        return value, action_log_probs, dist_entropy, states


def train(trajs, policy, args):
    #PARAMS
    lr = args.lr
    batch_size = args.batch
    loss_function = F.mse_loss
    decay = args.decay
    eval_period = args.eval
    memory_std = args.mem_std

    vis = visdom.Visdom()

    n = len(trajs)
    random.shuffle(trajs)
    trajs_test  = trajs[:n//10]
    trajs_train = trajs[n//10:]

    optimizer = optim.Adam(policy.parameters(), lr=lr, weight_decay=decay)
    def epoch(trajs):
        ids = np.random.permutation(len(trajs))
        for i in ids:
            yield trajs[i]

    def compute_loss(trajs):
        with th.no_grad():
            losses = []
            for obs_batch, act_batch in epoch(trajs):
                _, action, _, _ = policy.act(obs_batch, deterministic=True)
                losses.append(loss_function(act_batch, action).cpu().detach().numpy())
            return np.mean(np.array(losses))

    def opt():
        pairs = 0
        for obs_batch, act_batch in epoch(trajs_train):
            optimizer.zero_grad()
            _, action, _, _ = policy.act(obs_batch, deterministic=True)
            loss = loss_function(act_batch, action)
            loss.backward()
            optimizer.step()
            pairs += obs_batch.shape[0]
        return pairs

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        initial_loss = [compute_loss(trajs_train), compute_loss(trajs_test)]
        opts = dict(
            title='data=%s lr=%.1e bs=%i dc=%.1e mem=%.1e' % (args.dataset, lr, batch_size, decay, memory_std),
            xlabel='epochs'
        )
        opts['ylabel'] = 'loss'
        loss_plot = vis.line(np.array([[0, 0]]), np.array([0]),
            opts=opts)
        opts['ylabel'] = 'success'
        reward_plot = vis.line(np.array([0]), np.array([0]),
            opts=opts)
        rewards_futures = []
        epochs = 1000000

        print('initial loss=(%f, %f)' % (initial_loss[0], initial_loss[1]))
        cur_future = 0
        for e in range(epochs):
            start_time = time()
            pairs = opt()
            speed = pairs / (time() - start_time)
            if e % eval_period == 0:
                current_loss = [compute_loss(trajs_train), compute_loss(trajs_test)]
                vis.line(
                    np.array([current_loss]), np.array([e]),
                    win=loss_plot, update='append')
                print('epoch %i: \tloss_train=%.4f \tloss_test=%.4f \tspeed=%.1f' % (e, current_loss[0], current_loss[1], speed))
                th.save(policy, 'tmp/models/imitation_%i_epoch_latest' % args.gpu)
                if e % (eval_period * 50) == 0:
                    th.save(policy, 'tmp/models/imitation_%i_epoch_%i' % (args.gpu, e))
                #rewards_futures.append(executor.submit(estimate_reward, e, copy.deepcopy(policy), args.gpu))
            if len(rewards_futures) > cur_future:
                future = rewards_futures[cur_future]
                if future.done():
                    vis.line(
                        np.array([future.result()]), np.array([cur_future * eval_period]),
                        win=reward_plot, update='append')
                    cur_future += 1

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('dataset', action='store')
    params = {'lr': 1e-6, 'batch': 32, 'decay': 1e-2, 'eval': 100, 'gpu': 0, 'mem_std': 0, 'load': ''}
    for par, default in params.items():
        parser.add_argument('--'+par, action='store', default=default, type=type(default))
    args = parser.parse_args()

    with th.cuda.device(args.gpu):
        trajs = []
        for name in args.dataset.split(','):
            with h5py.File('tmp/imitation/' + name + '.h5', 'r') as f:
                obs = np.array(f['obsVar'])
                act = np.array(f['actVar'])
                env_info = np.array(f['envInfoVar'])
                n_samples = obs.shape[0]
                n_humans = obs.shape[1]
                obs_dim = obs.shape[2]
                act_dim = act.shape[2]
                w, h, c = size(obs_dim, 2)
                print('w = %i, h = %i, c = %i' % (w, h, c))
                for human in range(n_humans):
                    traj_start = 0
                    while traj_start < n_samples:
                        traj_end = traj_start
                        while traj_end < n_samples and env_info[traj_end, human, 1] == 0:
                            traj_end += 1
                        if traj_end > traj_start and traj_end < n_samples:
                            n = traj_end+1 - traj_start
                            trajs.append((
                                th.from_numpy(
                                    obs[traj_start:traj_end+1, human, :].reshape((n, h, w, c)).transpose(0, 3, 1, 2)
                                ).cuda(),
                                th.from_numpy(
                                    act[traj_start:traj_end+1, human, :].reshape((n, act_dim))
                                ).cuda(),
                            ))
                        traj_start = traj_end + 1
        print('#trajs = %i' % len(trajs))
        policy = None
        if args.load == '':
            policy = Policy(w, h, c, act_dim, args.mem_std)
        else:
            policy = th.load(args.load, map_location=lambda storage, loc: storage.cuda(args.gpu))
        train(
            trajs,
            policy,
            args
        )

def estimate_reward(epoch, policy, gpu):
    with th.cuda.device(gpu):
        try:
            print('estimating %i...' % epoch)
            env = minecraft.environment.MinecraftEnv('Pattern')
            env.init_spaces()
            n_eps = 50
            w, h, c = size(env.observation_dim, 2)
            def act(obs, states):
                with th.no_grad():
                    obs_th = th.from_numpy(obs.reshape((1, h, w, c)).transpose(0, 3, 1, 2)).float().cuda()
                    _, action, _, states = policy.act(obs_th, states, deterministic=True)
                    action = action.cpu().detach().numpy()
                return action, states
            mean_rew = 0
            for i in range(n_eps):
                obs = env.reset()
                ep_rew = 0
                states = None
                while True:
                    action, states = act(obs, states)
                    obs, rew, done, _ = env.step(action)
                    ep_rew += rew
                    if done:
                        ep_rew = 100 if ep_rew > 0 else 0
                        mean_rew += ep_rew / n_eps
                        break
            print('estimated %.2f for epoch %i' % (mean_rew, epoch))
            return mean_rew
        except:
            traceback.print_exc()

if __name__ == '__main__':
    main()
