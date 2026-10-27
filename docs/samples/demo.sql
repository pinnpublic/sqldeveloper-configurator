-- SQL Developer Configurator: display-only examples.
-- This file does not create, update or delete database objects.

select '화면 너비를 넘어가는 긴 SQL은 실제 개행을 넣지 않고 표시할 때만 자동으로 줄바꿈됩니다. ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789' as example_text from dual;

---

select '안녕하세요. --- 입니다' as ordinary_text from dual;

-- The line above contains other text, so it is not a horizontal rule.
-- To remove a rule, remove one hyphen from its standalone --- line.
