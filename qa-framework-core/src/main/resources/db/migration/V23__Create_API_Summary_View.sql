-- View for quick API specification summary
CREATE OR REPLACE VIEW api_specification_summary AS
SELECT
    s.id,
    s.name,
    s.version,
    s.openapi_version,
    s.base_url,
    s.endpoint_count,
    s.schema_count,
    s.uploaded_at,
    s.uploaded_by,
    s.is_active,
    COUNT(DISTINCT e.id) AS actual_endpoint_count,
    COUNT(DISTINCT sc.id) AS actual_schema_count,
    COUNT(DISTINCT tgc.test_id) AS tests_generated
FROM api_specifications s
LEFT JOIN api_endpoints e ON e.spec_id = s.id
LEFT JOIN api_schemas sc ON sc.spec_id = s.id
LEFT JOIN test_generation_context tgc ON tgc.spec_id = s.id
GROUP BY s.id;

COMMENT ON VIEW api_specification_summary IS 'Summary view of API specifications with counts';