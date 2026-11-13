(function () {
  "use strict";
  const data = JSON.parse(document.getElementById("graph-data").textContent);
  const t = JSON.parse(document.getElementById("language-data").textContent);
  const byId = new Map(data.nodes.map(function (n) { return [n.id, n]; }));
  const outgoing = new Map(), incoming = new Map();
  data.edges.forEach(function (e) {
    if (!outgoing.has(e.source)) outgoing.set(e.source, []);
    if (!incoming.has(e.target)) incoming.set(e.target, []);
    outgoing.get(e.source).push(e); incoming.get(e.target).push(e);
  });
  const order = ["DeploymentNode", "Device", "Point", "AlarmPolicy", "TriggerCondition", "DeviceTemplate", "PointTemplate"];
  const colors = ["#7f8ed1", "#dfb642", "#55bda7", "#eea36b", "#e9828e", "#a595c5", "#80b6cd"];
  const kindIndex = new Map(order.map(function (k, i) { return [k, i]; }));
  const format = new Intl.NumberFormat("zh-CN");
  const el = function (id) { return document.getElementById(id); };
  document.querySelectorAll("[data-i18n]").forEach(function (node) { node.textContent = t[node.dataset.i18n]; });
  document.title = t.title;
  el("search").placeholder = t.search;
  el("search").setAttribute("aria-label", t.search);
  el("global-nodes").textContent = format.format(data.nodes.length);
  el("global-edges").textContent = format.format(data.edges.length);
  el("snapshot").textContent = data.snapshot;
  order.forEach(function (kind, index) {
    const row = document.createElement("div"); row.className = "legend-item";
    const dot = document.createElement("span"); dot.className = "dot"; dot.style.background = colors[index];
    const label = document.createElement("span"); label.textContent = t.categories[kind];
    const count = document.createElement("span"); count.className = "count"; count.textContent = format.format(data.counts[kind] || 0);
    row.append(dot, label, count); el("legend").append(row);
  });
  const chart = echarts.init(el("chart"), null, { renderer: "canvas" });
  let selected = null, current = null, labelsOn = false;
  function frame() {
    return { left: 0, top: 0, right: 0, bottom: 0, width: "100%", height: "100%" };
  }
  const positions = new Map();
  function hash(value) {
    let h = 2166136261;
    for (let i = 0; i < value.length; i++) h = Math.imul(h ^ value.charCodeAt(i), 16777619);
    return h >>> 0;
  }
  function position(id, depth) {
    if (positions.has(id)) return positions.get(id);
    const n = byId.get(id);
    if (!n || depth > 12) return [0, 0];
    let anchor = null, radius = 60;
    if (n.kind === "DeploymentNode") {
      const parent = (outgoing.get(id) || []).find(function (e) { return e.type === "CHILD_OF"; });
      if (parent) { anchor = position(parent.target, depth + 1); radius = n.props.level === 2 ? 300 : 135; }
      else {
        const roots = data.nodes.filter(function (x) { return x.kind === "DeploymentNode" && !(outgoing.get(x.id) || []).some(function (e) { return e.type === "CHILD_OF"; }); });
        const angle = 2 * Math.PI * roots.findIndex(function (x) { return x.id === id; }) / Math.max(1, roots.length) - Math.PI / 2;
        const point = [Math.cos(angle) * 700, Math.sin(angle) * 700]; positions.set(id, point); return point;
      }
    } else if (n.kind === "Device") {
      const parent = (outgoing.get(id) || []).find(function (e) { return e.type === "DEPLOYED_IN"; });
      anchor = parent ? position(parent.target, depth + 1) : [0, 1050]; radius = 40 + hash(id) % 110;
    } else if (n.kind === "Point") {
      const parent = (incoming.get(id) || []).find(function (e) { return e.type === "HAS_POINT"; });
      anchor = parent ? position(parent.source, depth + 1) : [0, 0]; radius = 18 + hash(id) % 45;
    }
    const angle = (hash(id) % 3600) * Math.PI / 1800;
    if (!anchor) radius = { AlarmPolicy: 1250, TriggerCondition: 1500, DeviceTemplate: 1750, PointTemplate: 1950 }[n.kind] || 1000;
    const point = [(anchor ? anchor[0] : 0) + Math.cos(angle) * radius, (anchor ? anchor[1] : 0) + Math.sin(angle) * radius];
    positions.set(id, point); return point;
  }
  data.nodes.forEach(function (n) { position(n.id, 0); });
  function name(n) {
    if (n.props.name) return n.props.name;
    if (n.kind === "TriggerCondition") return n.props.kind === "numeric"
      ? String(n.props.operator || "") + " " + String(n.props.threshold || "") : String(n.props.enumValue || n.id);
    return n.id;
  }
  function escape(value) {
    return String(value).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; });
  }
  function showSelection(id, highlight) {
    const node = byId.get(id);
    if (!node) return;
    selected = node;
    const content = el("selection"); content.replaceChildren();
    const title = document.createElement("div"); title.className = "node-name"; title.textContent = name(node);
    const category = document.createElement("span"); category.className = "node-kind"; category.textContent = t.categories[node.kind];
    const uid = document.createElement("div"); uid.className = "node-id"; uid.textContent = node.id;
    content.append(category, title, uid);
    Object.entries(node.props).forEach(function (entry) {
      if (entry[1] === null || entry[1] === "" || entry[0] === "name") return;
      const row = document.createElement("div"); row.className = "property";
      const key = document.createElement("div"); key.className = "key"; key.textContent = t.properties[entry[0]] || entry[0];
      const value = document.createElement("div"); value.className = "value"; value.textContent = String(entry[1]);
      row.append(key, value); content.append(row);
    });
    el("expand").disabled = false;
    if (highlight) {
      chart.dispatchAction({ type: "downplay", seriesIndex: 0 });
      chart.dispatchAction({ type: "highlight", seriesIndex: 0, dataIndex: current.nodes.findIndex(function (n) { return n.id === id; }) });
    }
  }
  function render(ids, mode, title, hint) {
    el("loading").hidden = false;
    setTimeout(function () {
      const nodes = data.nodes.filter(function (n) { return ids.has(n.id); });
      const edges = data.edges.filter(function (e) { return ids.has(e.source) && ids.has(e.target); });
      const full = mode === "full", large = nodes.length > 700;
      current = { nodes: nodes, edges: edges, mode: mode };
      document.querySelectorAll("nav button").forEach(function (b) { b.classList.toggle("active", b.id === mode); });
      el("shown-nodes").textContent = format.format(nodes.length);
      el("shown-edges").textContent = format.format(edges.length);
      el("view-title").textContent = title; el("view-hint").textContent = hint;
      const canvasNodes = nodes.map(function (n) {
        const p = positions.get(n.id), category = kindIndex.get(n.kind);
        const size = full ? { DeploymentNode: 17, Device: 8, Point: 2.6, AlarmPolicy: 5, TriggerCondition: 3.2, DeviceTemplate: 7, PointTemplate: 3.2 }[n.kind]
          : mode === "overview" ? (n.kind === "DeploymentNode" ? (n.props.level === 1 ? 38 : 24) : 12) : n.kind === "Device" ? 40 : 22;
        return { id: n.id, name: name(n), category: category, x: (large || full) ? p[0] : undefined, y: (large || full) ? p[1] : undefined, symbolSize: size,
          itemStyle: { color: colors[category], borderColor: "#ffffff", borderWidth: full ? 0 : 1.5 },
          label: { show: labelsOn || (!full && (mode !== "overview" || n.kind === "DeploymentNode")),
            color: "#70818b", fontSize: mode === "overview" ? 10 : 11, position: "right",
            formatter: function (params) { const value = params.data.name; return value.length > 25 ? value.slice(0, 24) + "…" : value; } },
          originalId: n.id };
      });
      chart.setOption({
        animation: false,
        backgroundColor: "transparent",
        tooltip: { trigger: "item", confine: true, formatter: function (params) {
          if (params.dataType === "edge") return escape(t.relations[params.data.type] || params.data.type);
          const n = byId.get(params.data.originalId);
          return "<b>" + escape(name(n)) + "</b><br>" + escape(t.categories[n.kind]) + "<br>" + escape(n.id);
        } },
        series: [{
          type: "graph", layout: (large || full) ? "none" : "force",
          ...frame(),
          roam: true, draggable: !full, scaleLimit: { min: 0.03, max: 20 },
          zoom: mode === "overview" ? 0.62 : 0.9,
          force: { initLayout: "circular", repulsion: mode === "overview" ? 110 : 600,
            gravity: 0.04, edgeLength: mode === "overview" ? [40, 85] : [70, 130], friction: 0.6, layoutAnimation: true },
          data: canvasNodes,
          links: edges.map(function (e) { return { source: e.source, target: e.target, type: e.type,
            lineStyle: { color: colors[kindIndex.get(byId.get(e.source).kind)], opacity: full ? 0.08 : 0.33, width: full ? 0.4 : 1.1 } }; }),
          categories: order.map(function (kind) { return { name: t.categories[kind] }; }),
          edgeSymbol: ["none", full ? "none" : "arrow"], edgeSymbolSize: 5,
          edgeLabel: { show: mode !== "overview" && !full && edges.length < 100,
            fontSize: 10, color: "#8799a4", formatter: function (p) { return t.relations[p.data.type] || p.data.type; } },
          emphasis: { focus: "adjacency", scale: full ? 3 : 1.4, label: { show: true },
            lineStyle: { opacity: 0.9, width: 2 } },
          blur: { itemStyle: { opacity: 0.13 }, lineStyle: { opacity: 0.025 }, label: { opacity: 0.12 } }
        }]
      }, true);
      if (selected && ids.has(selected.id)) showSelection(selected.id);
      el("loading").hidden = true;
      window.__N4_GRAPH_VIEW__ = { mode: mode, nodes: nodes.length, edges: edges.length,
        globalNodes: data.nodes.length, globalEdges: data.edges.length, snapshot: data.snapshot,
        layout: full || large ? "fixed" : "force" };
    }, 30);
  }
  function overview() {
    render(new Set(data.nodes.filter(function (n) { return n.kind === "Device" || n.kind === "DeploymentNode"; }).map(function (n) { return n.id; })),
      "overview", t.overview, t.overviewHint);
  }
  function device(id) {
    const ids = new Set([id]);
    const points = (outgoing.get(id) || []).filter(function (e) { return e.type === "HAS_POINT"; }).map(function (e) { return e.target; });
    points.forEach(function (p) {
      ids.add(p);
      (outgoing.get(p) || []).forEach(function (e) {
        if (e.type === "INSTANCE_OF") ids.add(e.target);
        if (e.type === "USES_POLICY") {
          ids.add(e.target);
          (outgoing.get(e.target) || []).filter(function (x) { return x.type === "HAS_CONDITION"; }).forEach(function (x) { ids.add(x.target); });
        }
      });
    });
    (outgoing.get(id) || []).forEach(function (e) {
      if (e.type === "INSTANCE_OF") ids.add(e.target);
      if (e.type === "DEPLOYED_IN") {
        let cursor = e.target;
        for (let i = 0; i < 12 && cursor; i++) {
          ids.add(cursor);
          const parent = (outgoing.get(cursor) || []).find(function (x) { return x.type === "CHILD_OF"; });
          cursor = parent ? parent.target : null;
        }
      }
    });
    selected = byId.get(id);
    render(ids, id === "device:43" ? "example" : "detail", name(selected), t.detailHint);
  }
  function expand(id) {
    const n = byId.get(id);
    if (n.kind === "Device") { device(id); return; }
    if (n.kind === "Point") {
      const parent = (incoming.get(id) || []).find(function (e) { return e.type === "HAS_POINT"; });
      if (parent) { device(parent.source); return; }
    }
    const ids = new Set([id]);
    (outgoing.get(id) || []).concat(incoming.get(id) || []).slice(0, 599).forEach(function (e) { ids.add(e.source); ids.add(e.target); });
    selected = n;
    render(ids, "detail", name(n), t.neighborHint);
  }
  chart.on("click", function (params) {
    if (params.dataType === "node") showSelection(params.data.originalId, true);
  });
  chart.on("dblclick", function (params) {
    if (params.dataType === "node") expand(params.data.originalId);
  });
  el("overview").onclick = overview;
  el("full").onclick = function () { render(new Set(byId.keys()), "full", t.full, t.fullHint); };
  el("example").onclick = function () { device("device:43"); };
  el("expand").onclick = function () { if (selected) expand(selected.id); };
  el("fit").onclick = function () { chart.dispatchAction({ type: "restore" }); };
  el("labels").onchange = function () {
    labelsOn = this.checked;
    if (current) render(new Set(current.nodes.map(function (n) { return n.id; })), current.mode, el("view-title").textContent, el("view-hint").textContent);
  };
  el("search-form").onsubmit = function (event) {
    event.preventDefault();
    const term = el("search").value.trim().toLowerCase(), box = el("results");
    box.replaceChildren(); box.hidden = !term; if (!term) return;
    const aliases = t.searchAliases || {};
    const terms = [term].concat(aliases[term] || []);
    const matches = data.nodes.filter(function (n) {
      const text = (n.id + " " + Object.values(n.props).join(" ")).toLowerCase();
      return terms.some(function (x) { return text.includes(x); });
    }).sort(function (a, b) {
      return (order.indexOf(a.kind) - order.indexOf(b.kind)) || name(a).localeCompare(name(b));
    }).slice(0, 30);
    const heading = document.createElement("p"); heading.textContent = matches.length ? t.searchCount : t.searchEmpty; box.append(heading);
    matches.forEach(function (n) {
      const button = document.createElement("button"); button.type = "button";
      button.textContent = t.categories[n.kind] + " · " + name(n);
      button.onclick = function () { box.hidden = true; expand(n.id); };
      box.append(button);
    });
  };
  document.addEventListener("click", function (event) { if (!el("search-form").contains(event.target)) el("results").hidden = true; });
  window.addEventListener("resize", function () { chart.resize(); chart.setOption({ series: [frame()] }); });
  if (new URLSearchParams(window.location.search).get("view") === "full") el("full").onclick();
  else overview();
}());
