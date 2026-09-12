import assert from 'node:assert/strict'
import { readFile, access } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

test('TemplateDiagramDialog defines and resolves diagrams across Device-1, Device-2, and BA', async () => {
  const componentPath = fileURLToPath(new URL('../src/views/device-deploy/dialogs/TemplateDiagramDialog.vue', import.meta.url))
  const source = await readFile(componentPath, 'utf8')

  // Verify option groups defined in component
  assert.ok(source.includes("label: 'Device-1 (Primary Devices)'"), 'Must include Device-1 group')
  assert.ok(source.includes("label: 'Device-2 (Vendor & Secondary Devices)'"), 'Must include Device-2 group')
  assert.ok(source.includes("label: 'Building Automation & System (BA)'"), 'Must include BA group')

  // Verify el-option-group in template
  assert.ok(source.includes('<el-option-group'), 'Must use el-option-group in template select')
  assert.ok(source.includes('{{ currentDiagramUrl }}'), 'Path tag must show dynamic currentDiagramUrl')

  // Extract all referenced image files and urls from imageGroups in source
  const urlMatches = [...source.matchAll(/url:\s*'([^']+)'/g)].map(m => m[1])
  assert.ok(urlMatches.length >= 35, `Expected at least 35 diagram options, found ${urlMatches.length}`)

  // Verify all diagram image files exist on disk
  for (const url of urlMatches) {
    const relativePath = `../public${url}`
    const fullPath = fileURLToPath(new URL(relativePath, import.meta.url))
    await assert.doesNotReject(access(fullPath), `Asset file must exist on disk: ${url}`)
  }

  // Define matching function reflecting component logic
  const imageGroups = [
    {
      label: 'Device-1 (Primary Devices)',
      options: [
        { label: 'DCPmv', value: 'DCPmv.png', url: '/device-images/Device-1/DCPmv.png', keywords: ['dcpmv', 'dcp_zhengxiang_mv', 'dcp_mv', 'dcplv', 'dcp'] },
        { label: 'DEHU', value: 'DEHU.png', url: '/device-images/Device-1/DEHU.png', keywords: ['dehu_oulandis', 'dehu'] },
        { label: 'DHG', value: 'DHG.png', url: '/device-images/Device-1/DHG.png', keywords: ['dhg', 'fxd860sa'] },
        { label: 'EM33b', value: 'EM33b.png', url: '/device-images/Device-1/EM33b.png', keywords: ['spm33b', 'spm33a', 'spm32rmu', 'spm32', 'em33b', 'em33a'] },
        { label: 'EM33c', value: 'EM33c.png', url: '/device-images/Device-1/EM33c.png', keywords: ['spm33c', 'em33c'] },
        { label: 'EM770', value: 'EM770.png', url: '/device-images/Device-1/EM770.png', keywords: ['spm770h', 'spm770', 'em770'] },
        { label: 'EM780', value: 'EM780.png', url: '/device-images/Device-1/EM780.png', keywords: ['spm780h', 'spm781h', 'em780', 'em781'] },
        { label: 'GAS', value: 'GAS.png', url: '/device-images/Device-1/GAS.png', keywords: ['gas', 'yaoan'] },
        { label: 'IRC', value: 'IRC.png', url: '/device-images/Device-1/IRC.png', keywords: ['irc', 'ymk'] },
        { label: 'NWLD', value: 'NWLD.png', url: '/device-images/Device-1/NWLD.png', keywords: ['nwld'] },
        { label: 'ZBmvbus', value: 'ZBmvbus.png', url: '/device-images/Device-1/ZBmvbus.png', keywords: ['mvbustie', 'zbmvbus', 'rmu'] },
        { label: 'ZBmvin', value: 'ZBmvin.png', url: '/device-images/Device-1/ZBmvin.png', keywords: ['mvincomer', 'zbmvin'] },
        { label: 'ZBmvout', value: 'ZBmvout.png', url: '/device-images/Device-1/ZBmvout.png', keywords: ['mvfeeder', 'zbmvout'] },
      ],
    },
    {
      label: 'Device-2 (Vendor & Secondary Devices)',
      options: [
        { label: 'CET', value: 'CET.png', url: '/Device-2/CET.png', keywords: ['s963', 'cet'] },
        { label: 'ET903', value: 'ET903.png', url: '/Device-2/ET903.png', keywords: ['et903', 'yada'] },
        { label: 'Envicool', value: 'Envicool.png', url: '/Device-2/Envicool.png', keywords: ['envicool', 'fwu'] },
        { label: 'ImpowerBS', value: 'ImpowerBS.png', url: '/Device-2/ImpowerBS.png', keywords: ['impower', 'apf', 'battery', 'vertivxwd'] },
        { label: 'LD300', value: 'LD300.png', url: '/Device-2/LD300.png', keywords: ['lds3000', 'pwld_lds3000', 'pwld', 'ld300'] },
        { label: 'LD3', value: 'LD3.png', url: '/Device-2/LD3.png', keywords: ['lds3', 'nwld_lds3', 'ld3'] },
        { label: 'PDU_Distribution', value: 'PDU_Distribution.png', url: '/Device-2/PDU_Distribution.png', keywords: ['pdu', 'rpp', 'zh16'] },
        { label: 'SOCOMEC', value: 'SOCOMEC.png', url: '/Device-2/SOCOMEC.png', keywords: ['socomec', 'sts', 'ups_vertiv', 'ups'] },
        { label: 'STAND', value: 'STAND.png', url: '/Device-2/STAND.png', keywords: ['crah', 'stand'] },
      ],
    },
    {
      label: 'Building Automation & System (BA)',
      options: [
        { label: 'BA_ATS_Dual_Power_Switch', value: 'BA_ATS_Dual_Power_Switch.png', url: '/BA/BA_ATS_Dual_Power_Switch.png', keywords: ['ats', 'dual_power', 'switch_transfer'] },
        { label: 'Schneider_PM5300', value: 'Schneider_PM5300.png', url: '/BA/Schneider_PM5300.png', keywords: ['pm5300', 'schneider_pm'] },
        { label: 'ZB_Schneider_P3U30_DGincomer', value: 'ZB_Schneider_P3U30_DGincomer.png', url: '/BA/ZB_Schneider_P3U30_DGincomer.png', keywords: ['p3u30_dgincomer', 'p3u30_dg', 'dgincomer'] },
        { label: 'ZB_Schneider_P3U30_TRincomer', value: 'ZB_Schneider_P3U30_TRincomer.png', url: '/BA/ZB_Schneider_P3U30_TRincomer.png', keywords: ['p3u30_trincomer', 'p3u30_tr', 'trincomer'] },
        { label: 'CH_A01_Chiller', value: 'CH_A01_Chiller.png', url: '/BA/CH_A01_Chiller.png', keywords: ['chiller', 'ch_a01'] },
        { label: 'BA_Datahall_GA_CDU', value: 'BA_Datahall_GA_CDU.png', url: '/BA/BA_Datahall_GA_CDU.png', keywords: ['cdu', 'datahall_cdu'] },
        { label: 'BA_Dosing_Constant_Pressure', value: 'BA_Dosing_Constant_Pressure.png', url: '/BA/BA_Dosing_Constant_Pressure.png', keywords: ['dosing', 'constant_pressure'] },
        { label: 'BA_Makeup_Water_System', value: 'BA_Makeup_Water_System.png', url: '/BA/BA_Makeup_Water_System.png', keywords: ['makeup_water', 'water_system'] },
        { label: 'BA_HVAC_Multi_System', value: 'BA_HVAC_Multi_System.png', url: '/BA/BA_HVAC_Multi_System.png', keywords: ['hvac', 'multi_system'] },
        { label: 'BA_Water_Cooling_System_A', value: 'BA_Water_Cooling_System_A.png', url: '/BA/BA_Water_Cooling_System_A.png', keywords: ['water_cooling'] },
        { label: 'BA_Network_Topology_PLC', value: 'BA_Network_Topology_PLC.png', url: '/BA/BA_Network_Topology_PLC.png', keywords: ['topology_plc', 'network_plc'] },
        { label: 'BA_Network_Topology_Datahall_PLC', value: 'BA_Network_Topology_Datahall_PLC.png', url: '/BA/BA_Network_Topology_Datahall_PLC.png', keywords: ['datahall_plc'] },
        { label: 'BA_Unit_Parameters_Alarms', value: 'BA_Unit_Parameters_Alarms.png', url: '/BA/BA_Unit_Parameters_Alarms.png', keywords: ['parameters_alarms', 'unit_alarms'] },
      ],
    },
  ]

  const allImages = imageGroups.flatMap(g => g.options)

  function findBestMatchingImage(target) {
    if (!target) return allImages[0].value
    const candidates = [
      target.facePlateId,
      target.deviceTmplName,
      target.model,
      target.deviceType,
      target.manufacturer,
    ].filter(Boolean).map(s => String(s).trim().toLowerCase())

    const candidateCombined = candidates.join(' ')

    const keywordEntries = []
    for (const item of allImages) {
      if (item.keywords) {
        for (const kw of item.keywords) {
          keywordEntries.push({ kw, value: item.value })
        }
      }
    }
    keywordEntries.sort((a, b) => b.kw.length - a.kw.length)

    for (const { kw, value } of keywordEntries) {
      if (candidateCombined.includes(kw)) {
        return value
      }
    }

    for (const item of allImages) {
      const rawName = item.value.replace(/\.(png|jpg|jpeg|svg)$/i, '').toLowerCase()
      for (const cand of candidates) {
        if (cand === rawName || cand.includes(rawName) || rawName.includes(cand)) {
          return item.value
        }
      }
    }

    return allImages[0].value
  }

  // Test Device-1 matching
  assert.equal(findBestMatchingImage({ deviceTmplName: 'EM_Pilot_SPM770H', deviceType: 'EM' }), 'EM770.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'EM_Pilot_SPM780H', deviceType: 'EM' }), 'EM780.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'ZB_EATON_FXD860S_MVincomer', deviceType: 'ZB' }), 'ZBmvin.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'ZB_EATON_FXD860S_MVfeeder', deviceType: 'ZB' }), 'ZBmvout.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'ZB_EATON_FXD860S_MVbustie', deviceType: 'ZB' }), 'ZBmvbus.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'DEHU_Oulandis', deviceType: 'DEHU' }), 'DEHU.png')

  // Test Device-2 matching
  assert.equal(findBestMatchingImage({ deviceTmplName: 'EM_YADA_ET903', deviceType: 'EM' }), 'ET903.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'EM_CET_S963', deviceType: 'EM' }), 'CET.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'FWU_Envicool', deviceType: 'FWU' }), 'Envicool.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'STS_Socomec_Jbus', deviceType: 'STS' }), 'SOCOMEC.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'UPS_Vertiv9000', deviceType: 'UPS' }), 'SOCOMEC.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'APF_Impower', deviceType: 'APF' }), 'ImpowerBS.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'Battery_VertivXWD', deviceType: 'Battery' }), 'ImpowerBS.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'PWLD_LDS3000', deviceType: 'PWLD' }), 'LD300.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'NWLD_LDS3', deviceType: 'NWLD' }), 'LD3.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'DEHU_STAND_CRAH', deviceType: 'DEHU' }), 'STAND.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'RPP_ZH16', deviceType: 'RPP' }), 'PDU_Distribution.png')

  // Test BA matching
  assert.equal(findBestMatchingImage({ deviceTmplName: 'ZB_Schneider_P3U30_DGincomer', deviceType: 'ZB' }), 'ZB_Schneider_P3U30_DGincomer.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'ZB_Schneider_P3U30_TRincomer', deviceType: 'ZB' }), 'ZB_Schneider_P3U30_TRincomer.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'Schneider_PM5300', deviceType: 'EM' }), 'Schneider_PM5300.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'BA_ATS_Dual_Power_Switch', deviceType: 'ATS' }), 'BA_ATS_Dual_Power_Switch.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'CH_A01_Chiller', deviceType: 'Chiller' }), 'CH_A01_Chiller.png')

  // Test Fallback
  assert.equal(findBestMatchingImage(null), 'DCPmv.png')
  assert.equal(findBestMatchingImage({ deviceTmplName: 'Unknown_Custom_Device', deviceType: 'Custom' }), 'DCPmv.png')
})
