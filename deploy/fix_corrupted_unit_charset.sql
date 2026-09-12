-- ====================================================================
-- Script to repair corrupted degree Celsius units in N4 Monitor tables
-- Repaired targets:
-- 1. point_template
-- 2. point_deployment
-- ====================================================================

-- Update corrupted units in point_template
UPDATE `point_template`
SET `unit` = _utf8mb4 0xC2B043
WHERE `unit` LIKE CONCAT('%', _utf8mb4 0xE68EB343, '%')
   OR `unit` LIKE CONCAT('%', _utf8mb4 0xE692B843, '%')
   OR `unit` LIKE '%?C%'
   OR `unit` LIKE CONCAT('%', _utf8mb4 0xEFBFBD43, '%');

-- Update corrupted units in point_deployment
UPDATE `point_deployment`
SET `unit` = _utf8mb4 0xC2B043
WHERE `unit` LIKE CONCAT('%', _utf8mb4 0xE68EB343, '%')
   OR `unit` LIKE CONCAT('%', _utf8mb4 0xE692B843, '%')
   OR `unit` LIKE '%?C%'
   OR `unit` LIKE CONCAT('%', _utf8mb4 0xEFBFBD43, '%');
