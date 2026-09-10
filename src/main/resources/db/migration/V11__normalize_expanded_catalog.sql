UPDATE readings
SET title = CASE id
    WHEN '30000000-0000-0000-0000-000000000019' THEN 'The Courtyard We Built Together'
    WHEN '30000000-0000-0000-0000-000000000041' THEN 'The Surveyor of Moving Shores'
    WHEN '30000000-0000-0000-0000-000000000042' THEN 'The Gallery of Open Questions'
    ELSE title
END,
content = replace(content, '\n\n', E'\n\n')
WHERE id::text LIKE '30000000-%';
