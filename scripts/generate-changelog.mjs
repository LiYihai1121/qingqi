#!/usr/bin/env node
// Changelog 草稿生成器（DESIGN-VERSION-MANAGEMENT 第 4.3 节）。
//
// 用法：node scripts/generate-changelog.mjs <prevTag> <newTag>
//
// 从两个 tag 之间的 Squash 提交按 Conventional Commits type 自动分节，产出
// **草稿**——措辞与影响范围需人工定稿后再写入 CHANGELOG.md。脚本不直接修改
// CHANGELOG.md：它是「用户可读文档」而非日志转储，机械归纳与人工措辞分层。
//
// 分节规则：feat → Added；fix → Fixed；其余 type → Changed；
// 标题带 `!` 或正文含 `BREAKING CHANGE:` 的条目单列到 BREAKING 段；
// 非 Conventional Commits 格式的主题进「未识别」段（提示人工归类）。

import { execFileSync } from "node:child_process";

const [, , prevTag, newTag] = process.argv;
if (!prevTag || !newTag) {
    console.error("用法: node scripts/generate-changelog.mjs <prevTag> <newTag>");
    process.exit(2);
}

// %s 标题 / %b 正文，用不可见分隔符切分，避免多行正文被按行误拆
const raw = execFileSync(
    "git",
    ["log", `${prevTag}..${newTag}`, "--format=%s%x1f%b%x1e"],
    { encoding: "utf8", maxBuffer: 16 * 1024 * 1024 },
);

const commits = raw
    .split("\x1e")
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => {
        const [subject = "", ...bodyLines] = chunk.split("\x1f");
        return { subject: subject.trim(), body: bodyLines.join("\x1f").trim() };
    });

const CONVENTIONAL =
    /^(feat|fix|docs|ci|test|refactor|chore|style|perf|build|revert)(\(([^)]*)\))?(!)?: (.+)$/;

const sections = { Added: [], Fixed: [], Changed: [] };
const breaking = [];
const unknown = [];

for (const { subject, body } of commits) {
    const match = CONVENTIONAL.exec(subject);
    if (!match) {
        unknown.push(`- ${subject}`);
        continue;
    }
    const [, type, , , bang, description] = match;
    const line = `- ${description}`;
    if (bang === "!" || /BREAKING CHANGE:/m.test(body)) {
        breaking.push(line);
    }
    if (type === "feat") {
        sections.Added.push(line);
    } else if (type === "fix") {
        sections.Fixed.push(line);
    } else {
        sections.Changed.push(line);
    }
}

let out = `## Changelog 草稿（${prevTag} → ${newTag}）\n\n`;
out += "> 自动草稿，措辞与影响范围需人工定稿后再写入 CHANGELOG.md。\n";
if (breaking.length > 0) {
    out += `\n### BREAKING CHANGE\n\n${breaking.join("\n")}\n`;
}
if (sections.Added.length > 0) {
    out += `\n### Added\n\n${sections.Added.join("\n")}\n`;
}
if (sections.Fixed.length > 0) {
    out += `\n### Fixed\n\n${sections.Fixed.join("\n")}\n`;
}
if (sections.Changed.length > 0) {
    out += `\n### Changed\n\n${sections.Changed.join("\n")}\n`;
}
if (unknown.length > 0) {
    out += `\n### ⚠️ 未识别格式（需人工归类）\n\n${unknown.join("\n")}\n`;
}

console.log(out.trimEnd());
