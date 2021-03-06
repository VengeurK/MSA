import subprocess

import torch
import torch.nn as nn
from rl.distributions import FixedNormal

class AddBias(nn.Module):
    def __init__(self, bias):
        super(AddBias, self).__init__()
        self._bias = nn.Parameter(bias).view(1, 1, -1).cuda()

    def forward(self, x):
        return x + self._bias

class DiagGaussian(nn.Module):
    def __init__(self, dim):
        super(DiagGaussian, self).__init__()

        self.logstd = AddBias(torch.zeros(dim))

    def forward(self, x):
        zeros = torch.zeros(x.size())
        if x.is_cuda:
            zeros = zeros.cuda()

        action_logstd = self.logstd(zeros)
        return FixedNormal(x, action_logstd.exp())

class Policy(nn.Module):
    def __init__(self, n_agents):
        super(Policy, self).__init__()
        self.n_agents = n_agents-1

        hidden = 16
        def mlp(i, o):
            return nn.Sequential(
                    nn.Linear(i, hidden),
                    nn.Tanh(),
                    nn.Linear(hidden, hidden),
                    nn.Tanh(),
                    nn.Linear(hidden, o)
                ).cuda()

        self.action = mlp(2, 2)
        self.critic = mlp(2, 1)

        self.dist = DiagGaussian(2).cuda()

        self.train()

        self.state_size = 1

    def _adapt_inputs(self, inputs):
        if len(inputs.shape) < 3:
            inputs = inputs.view(-1, 2, self.n_agents).permute(0, 2, 1)
        return inputs

    def _adapt_outputs(self, outputs, dim):
        return outputs.permute(0, 2, 1).contiguous().view(-1, dim * self.n_agents)

    def act(self, inputs, states=None, masks=None, deterministic=False):
        inputs = self._adapt_inputs(inputs)
        actor_features = self.action(inputs)
        dist = self.dist(actor_features)

        action = actor_features if deterministic else dist.rsample()

        action_log_probs = dist.log_probs(action)
        dist_entropy = dist.entropy().mean()

        value = torch.sum(self.critic(inputs), 2)
        return value, self._adapt_outputs(), action_log_probs.permute(0, 2, 1).view(-1, self.n_agents), states #TODO

    def get_value(self, inputs, states=None, masks=None):
        inputs = self._adapt_inputs(inputs)
        return torch.sum(self.critic(inputs), 2)

    def evaluate_actions(self, inputs, states, masks, action):
        inputs = self._adapt_inputs(inputs)
        value, actor_features = self.critic(inputs), self.action(inputs)
        value = torch.sum(value, 2)
        dist = self.dist(actor_features)

        action_log_probs = dist.log_probs(self._adapt_inputs(action))
        dist_entropy = dist.entropy().mean()

        return value, action_log_probs.permute(0, 2, 1).view(-1, self.n_agents), dist_entropy, states

if __name__ == '__main__':
    def create_policy(C):
        torch.save(Policy(C), "tmp/policy_%i" % C)

    Rs = [4, 3, 2, 1]
    Cs = [2, 3, 4, 5, 6, 7, 8, 9, 10]
    S = 1 #share factor (how many agents with same policy)

    for C in Cs:
        create_policy(C)

    commands = []
    for R in Rs:
        for C in Cs:
            cmd = ('python python/agent_rl.py --env-name mc.Trade[%i,%i,20].trade[%i,%i] --num-processes %i --no-vis --num-stack 1 ' +
                '--algo ppo --num-steps 500 --entropy-coef 0 --ppo-epoch 3 --lr 1e-3 --num-frames 20000 --load tmp/policy_%i') % (C, R, C, R, S, C)
            for i in range(C // S):
                commands.append(cmd + ' --seed %i' % i)
            if len(commands) > 6:
                processes = [subprocess.Popen(command.split()) for command in commands]
                for p in processes:
                    p.wait()
                commands = []
    if len(commands) > 0:
        processes = [subprocess.Popen(command.split()) for command in commands]
        for p in processes:
            p.wait()
        commands = []
