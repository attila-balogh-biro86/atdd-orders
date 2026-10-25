#!/usr/bin/env node
// Claude Code PreToolUse hook: keeps each agent role inside its lane.
//
//   ATDD_ROLE=implement (default)  may change src/**; must not touch the spec or governance
//   ATDD_ROLE=spec                 may change specs/**, acceptance/**, delivery/**; must not touch src/main/**
//   ATDD_ROLE=platform             unrestricted (pipeline / tooling maintenance, humans only)
//
// Exit code 2 blocks the tool call and feeds stderr back to Claude.
// This is a local guard rail; ci/guard.sh is the authoritative check on every PR.
import { readFileSync } from 'node:fs';
import path from 'node:path';

const role = (process.env.ATDD_ROLE || 'implement').toLowerCase();
if (role === 'platform') process.exit(0);

const input = JSON.parse(readFileSync(0, 'utf8') || '{}');
const tool = input.tool_name || '';
const ti = input.tool_input || {};
const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();

const GOVERNANCE = [/^\.github\//, /^\.claude\//, /^CLAUDE\.md$/, /^ci\//, /^scripts\//];
const SPEC = [/^specs\//, /^acceptance\//];
const IMPL = [/^src\/main\//];

const rules = {
  implement: { blocked: [...SPEC, ...GOVERNANCE],
    why: 'the executable specification and delivery governance are read-only for implementers. ' +
         'If a scenario seems wrong, stop and explain it in the PR description instead.' },
  spec: { blocked: [...IMPL, ...GOVERNANCE],
    why: 'spec work must not change production code (separate implementation PR) or delivery governance (platform team).' },
};
const rule = rules[role];
if (!rule) block(`Unknown ATDD_ROLE '${role}'. Use implement | spec | platform.`);

const rel = (p) => path.relative(projectDir, path.resolve(projectDir, p)).split(path.sep).join('/');
const isBlocked = (p) => rule.blocked.some((re) => re.test(rel(p)));

if (['Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(tool)) {
  const target = ti.file_path || ti.notebook_path;
  if (target && isBlocked(target)) {
    block(`Role '${role}' may not modify ${rel(target)}: ${rule.why}`);
  }
}

if (tool === 'Bash') {
  const cmd = String(ti.command || '');
  // Heuristic: collect paths that a shell command would WRITE to, then check them.
  const written = [];
  for (const m of cmd.matchAll(/>{1,2}\s*([^\s;&|]+)/g)) written.push(m[1]);                  // redirects
  for (const seg of cmd.split(/[;&|]+/)) {
    const words = seg.trim().split(/\s+/).filter((w) => w && !w.startsWith('-'));
    const [bin, ...args] = words;
    if (['rm', 'mv', 'tee', 'truncate', 'unlink', 'rmdir'].includes(bin)) written.push(...args);
    if (bin === 'cp' && args.length) written.push(args[args.length - 1]);
    if ((bin === 'sed' && /\s-i/.test(seg)) || (bin === 'perl' && /\s-p?i/.test(seg))) written.push(...args);
    if (bin === 'git' && ['checkout', 'restore', 'rm', 'mv'].includes(args[0])) written.push(...args.slice(1));
  }
  const hits = written.map((p) => p.replace(/^['"]|['"]$/g, '')).filter((p) => p && isBlocked(p));
  if (hits.length) {
    block(`Role '${role}' may not modify protected paths via shell (${hits.join(', ')}): ${rule.why}`);
  }
  if (role === 'implement' && /pending-requirements\.txt/.test(cmd) && />>/.test(cmd)) {
    block('Implementers may only remove requirement IDs from delivery/pending-requirements.txt, never add them.');
  }
}

process.exit(0);

function block(msg) {
  process.stderr.write(`[atdd-guard] ${msg}\n`);
  process.exit(2);
}
