import assert from 'node:assert/strict'
import { test } from 'node:test'
import { normalizeUnit } from '../src/utils/unit.ts'

test('normalizeUnit handles null, empty and normal values', () => {
  assert.equal(normalizeUnit(null), '')
  assert.equal(normalizeUnit(undefined), '')
  assert.equal(normalizeUnit(''), '')
  assert.equal(normalizeUnit('   '), '')
  assert.equal(normalizeUnit('kW'), 'kW')
  assert.equal(normalizeUnit('V'), 'V')
  assert.equal(normalizeUnit('A'), 'A')
  assert.equal(normalizeUnit('°C'), '°C')
})

test('normalizeUnit repairs corrupted degree Celsius variants', () => {
  // \u64B8C and \u63B3C are the GBK decodes of UTF-8 degree sign bytes followed by 'C'
  assert.equal(normalizeUnit('\u64B8C'), '°C')
  assert.equal(normalizeUnit('\u63B3C'), '°C')
  assert.equal(normalizeUnit('?C'), '°C')
  assert.equal(normalizeUnit('\uFFFDC'), '°C')
  assert.equal(normalizeUnit(' \u64B8c '), '°C')
  assert.equal(normalizeUnit(' \u63B3c '), '°C')
  assert.equal(normalizeUnit('?c'), '°C')
})
