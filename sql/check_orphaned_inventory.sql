-- Active gateways with no active laboratory, or with stale laboratory IDs.
SELECT g.id, g.gateway_name, g.using_in, g.accept_topic, g.send_topic,
       binding.laboratory_id AS missing_laboratory_id
FROM gateway g
LEFT JOIN JSON_TABLE(COALESCE(g.using_in, JSON_ARRAY()), '$[*]'
    COLUMNS (laboratory_id VARCHAR(64) PATH '$')) AS binding ON TRUE
LEFT JOIN laboratory l ON BINARY l.id = BINARY binding.laboratory_id AND l.delete_at IS NULL
WHERE g.delete_at IS NULL AND l.id IS NULL
ORDER BY g.id, binding.laboratory_id;

-- Active devices whose laboratory or RS485 gateway is missing or soft-deleted.
SELECT d.id, d.device_name, d.belong_to, d.gateway_id,
       l.id IS NULL AS missing_laboratory, g.id IS NULL AS missing_gateway
FROM device d
LEFT JOIN laboratory l ON l.id = d.belong_to AND l.delete_at IS NULL
LEFT JOIN gateway g ON g.id = d.gateway_id AND g.gateway_type = 'RS485' AND g.delete_at IS NULL
WHERE d.delete_at IS NULL AND (l.id IS NULL OR g.id IS NULL)
ORDER BY d.id;
