/**
 * @typedef {{ id: number, role: string, text: string, timestampMs: number }} HarnessMessage
 * @typedef {{ id: number, type: string, title: string, detail: string, timestampMs: number, correlationId?: string }} HarnessEvent
 * @typedef {{ id: string, question: string, options: string[], createdAtMs: number }} PendingAsk
 * @typedef {{ id: string, path: string, accessType: string, scopeOptions: string[], createdAtMs: number }} PendingPermission
 * @typedef {{ id: string, text: string, createdAtMs: number }} PendingMessage
 * @typedef {{ id: string, name: string, isBuiltIn: boolean, rules: Record<string, string> }} FilterProfile
 * @typedef {{ activeProfileId: string, profiles: FilterProfile[] }} FilterProfileState
 * @typedef {{ id: string, label: string, description: string }} ToolFilterGroup
 * @typedef {{ id: string, groupId: string, label: string, description: string }} ToolFilterSubgroup
 * @typedef {{ id: string, toolName: string, groupId: string, subgroupId?: string, label: string, description: string }} ToolFilterTool
 * @typedef {{ groups: ToolFilterGroup[], subgroups: ToolFilterSubgroup[], tools: ToolFilterTool[] }} ToolFilterCatalog
 * @typedef {{ id: string, label: string, description: string, isAvailable: boolean, error?: string }} McpToolFilterServer
 * @typedef {{ id: string, toolName: string, serverId: string, label: string, description: string }} McpToolFilterTool
 * @typedef {{ servers: McpToolFilterServer[], tools: McpToolFilterTool[] }} McpToolFilterCatalog
 * @typedef {{ id: string, name: string, provider: string, providerLabel: string }} SupportedModel
 * @typedef {{
 *   sessionId: string,
 *   kind: string,
 *   parentSessionId?: string,
 *   rootSessionId?: string,
 *   updatedAtMs: number,
 *   messageCount: number,
 *   pendingMessageCount: number,
 *   running: boolean,
 *   label: string
 * }} SessionSummary
 * @typedef {{
 *   running: boolean,
 *   selectedModelId: string,
 *   modelLabel: string,
 *   supportedModels: SupportedModel[],
 *   activeContextSize: number,
 *   messages: HarnessMessage[],
 *   activity: HarnessEvent[],
 *   pendingMessages: PendingMessage[]
 * }} HarnessSessionSnapshot
 * @typedef {{
 *   currentSessionId?: string,
 *   sessions: SessionSummary[],
 *   session: HarnessSessionSnapshot,
 *   pendingAskUsers: PendingAsk[],
 *   pendingPermissions: PendingPermission[],
 *   filterProfiles: FilterProfileState,
 *   toolFilterProfiles: FilterProfileState,
 *   toolFilterCatalog: ToolFilterCatalog,
 *   mcpToolFilterProfiles: FilterProfileState,
 *   mcpToolFilterCatalog: McpToolFilterCatalog
 * }} HarnessState
 */

const streamEl = document.getElementById("stream");
const agentViewEl = document.getElementById("agentView");
const filtersViewEl = document.getElementById("filtersView");
const toolFiltersViewEl = document.getElementById("toolFiltersView");
const commandDeckEl = document.getElementById("commandDeck");
const agentTabButtonEl = document.getElementById("agentTabButton");
const sessionsTabButtonEl = document.getElementById("sessionsTabButton");
const filtersTabButtonEl = document.getElementById("filtersTabButton");
const toolFiltersTabButtonEl = document.getElementById("toolFiltersTabButton");
const sessionsViewEl = document.getElementById("sessionsView");
const filterProfileSelectEl = document.getElementById("filterProfileSelect");
const toolFilterProfileSelectEl = document.getElementById("toolFilterProfileSelect");
const mcpToolFilterProfileSelectEl = document.getElementById("mcpToolFilterProfileSelect");
const modelSelectEl = document.getElementById("modelSelect");
const contextCardEl = document.getElementById("contextCard");
const activeContextSizeValueEl = document.getElementById("activeContextSizeValue");
const clearContextButtonEl = document.getElementById("clearContextButton");
const sessionCardEl = document.getElementById("sessionCard");
const statusHeadlineEl = document.getElementById("statusHeadline");
const statusSummaryEl = document.getElementById("statusSummary");
const currentSessionLabelEl = document.getElementById("currentSessionLabel");
const createSessionButtonEl = document.getElementById("createSessionButton");
const sessionListEl = document.getElementById("sessionList");
const composerEl = document.getElementById("composer");
const messageInputEl = document.getElementById("messageInput");
const sendButtonEl = document.getElementById("sendButton");
const feedItemTemplate = document.getElementById("feedItemTemplate");
const filterEditorSelectEl = document.getElementById("filterEditorSelect");
const newFilterProfileNameInputEl = document.getElementById("newFilterProfileNameInput");
const createFilterProfileButtonEl = document.getElementById("createFilterProfileButton");
const filterEditorHeadlineEl = document.getElementById("filterEditorHeadline");
const filterEditorHintEl = document.getElementById("filterEditorHint");
const filterProfileNameInputEl = document.getElementById("filterProfileNameInput");
const saveFilterProfileButtonEl = document.getElementById("saveFilterProfileButton");
const deleteFilterProfileButtonEl = document.getElementById("deleteFilterProfileButton");
const filterRuleListEl = document.getElementById("filterRuleList");
const toolFilterEditorSelectEl = document.getElementById("toolFilterEditorSelect");
const newToolFilterProfileNameInputEl = document.getElementById("newToolFilterProfileNameInput");
const createToolFilterProfileButtonEl = document.getElementById("createToolFilterProfileButton");
const toolFilterEditorHeadlineEl = document.getElementById("toolFilterEditorHeadline");
const toolFilterEditorHintEl = document.getElementById("toolFilterEditorHint");
const toolFilterProfileNameInputEl = document.getElementById("toolFilterProfileNameInput");
const saveToolFilterProfileButtonEl = document.getElementById("saveToolFilterProfileButton");
const deleteToolFilterProfileButtonEl = document.getElementById("deleteToolFilterProfileButton");
const toolFilterRuleListEl = document.getElementById("toolFilterRuleList");
const mcpToolFilterEditorSelectEl = document.getElementById("mcpToolFilterEditorSelect");
const newMcpToolFilterProfileNameInputEl = document.getElementById("newMcpToolFilterProfileNameInput");
const createMcpToolFilterProfileButtonEl = document.getElementById("createMcpToolFilterProfileButton");
const mcpToolFilterEditorHeadlineEl = document.getElementById("mcpToolFilterEditorHeadline");
const mcpToolFilterEditorHintEl = document.getElementById("mcpToolFilterEditorHint");
const mcpToolFilterProfileNameInputEl = document.getElementById("mcpToolFilterProfileNameInput");
const saveMcpToolFilterProfileButtonEl = document.getElementById("saveMcpToolFilterProfileButton");
const deleteMcpToolFilterProfileButtonEl = document.getElementById("deleteMcpToolFilterProfileButton");
const mcpToolFilterRuleListEl = document.getElementById("mcpToolFilterRuleList");

const HTTP_METHOD = Object.freeze({
  POST: "POST",
});

const POLICY_LIFETIME = Object.freeze({
  ONCE: "ONCE",
  SESSION: "SESSION",
  FOREVER: "FOREVER",
});

const ACTIVITY_EVENT_TYPE = Object.freeze({
  SYSTEM: "system",
  CONTROL: "control",
  MODEL: "model",
  THINKING: "thinking",
  TOOL_REQUEST: "tool-request",
  TOOL_RESULT: "tool-result",
  ERROR: "error",
  CUSTOM: "custom",
});

const FILTER_CATEGORY = Object.freeze({
  USER_MESSAGE: "user-message",
  ASSISTANT_MESSAGE: "assistant-message",
  SYSTEM: "system",
  CONTROL: "control",
  MODEL: "model",
  THINKING: "thinking",
  TOOL: "tool",
  ERROR: "error",
  CUSTOM: "custom",
  ASK: "ask",
  PERMISSION: "permission",
});

const DISPLAY_MODES = ["FULL", "MINIFIED", "HIDDEN"];
const TOOL_FILTER_GROUP_MODES = ["CUSTOM", "ENABLED", "DISABLED"];
const TOOL_FILTER_TOOL_MODES = ["ENABLED", "DISABLED"];
const FILTER_CATEGORY_META = [
  {
    key: FILTER_CATEGORY.USER_MESSAGE,
    label: "User messages",
    description: "Messages you send into the session.",
  },
  {
    key: FILTER_CATEGORY.ASSISTANT_MESSAGE,
    label: "Agent replies",
    description: "Final assistant responses in the conversation.",
  },
  {
    key: FILTER_CATEGORY.SYSTEM,
    label: "System events",
    description: "Session lifecycle items such as session readiness and prompt loading.",
  },
  {
    key: FILTER_CATEGORY.CONTROL,
    label: "Control events",
    description: "Run control actions such as stops, interrupts, and queued messages.",
  },
  {
    key: FILTER_CATEGORY.MODEL,
    label: "Model events",
    description: "Model switches and refresh notices.",
  },
  {
    key: FILTER_CATEGORY.THINKING,
    label: "Thinking output",
    description: "Model reasoning traces emitted into the stream.",
  },
  {
    key: FILTER_CATEGORY.TOOL,
    label: "Tool activity",
    description: "Tool requests and results grouped into one stream item.",
  },
  {
    key: FILTER_CATEGORY.ERROR,
    label: "Errors",
    description: "Session and model failures.",
  },
  {
    key: FILTER_CATEGORY.CUSTOM,
    label: "Custom events",
    description: "Other event types not yet split into their own category.",
  },
];

/** @type {HarnessState | null} */
let latestState = null;
let latestFingerprint = "";
let activeView = "agent";
let selectedFilterEditorProfileId = "";
let newFilterProfileName = "";
let selectedToolFilterEditorProfileId = "";
let newToolFilterProfileName = "";
let selectedMcpToolFilterEditorProfileId = "";
let newMcpToolFilterProfileName = "";
const askDrafts = new Map();
const permissionDrafts = new Map();
const filterProfileDrafts = new Map();
const toolFilterProfileDrafts = new Map();
const mcpToolFilterProfileDrafts = new Map();
const feedExpansionState = new Map();
let isSubmittingMessage = false;
let isClearingContext = false;
let isMutatingSession = false;
let composerStatus = { state: "idle", text: "" };
let composerStatusTimer = null;
let lastKnownActiveContextSize = 0;

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

async function loadState() {
  const nextState = await requestJson("/api/state");
  const nextFingerprint = JSON.stringify(nextState);

  syncActiveContextSize(nextState);
  latestState = nextState;
  syncDraftStores();

  if (nextFingerprint === latestFingerprint) {
    return;
  }

  latestFingerprint = nextFingerprint;
  render();
}

function render() {
  if (!latestState) return;

  const feedItems = buildFeedItems();
  syncExpansionState(feedItems);
  const visibleFeedItems = applyActiveFilter(feedItems);

  renderViewTabs();
  renderControls();
  renderSummary(feedItems);
  renderSessionList();
  renderStream(feedItems, visibleFeedItems);
  renderFilterView();
  renderToolFilterView();
  renderComposer();
}

function renderViewTabs() {
  const isAgentView = activeView === "agent";
  const isSessionsView = activeView === "sessions";
  const isEventFiltersView = activeView === "filters";
  const isToolFiltersView = activeView === "tool-filters";
  agentTabButtonEl.setAttribute("aria-selected", String(isAgentView));
  sessionsTabButtonEl.setAttribute("aria-selected", String(isSessionsView));
  filtersTabButtonEl.setAttribute("aria-selected", String(isEventFiltersView));
  toolFiltersTabButtonEl.setAttribute("aria-selected", String(isToolFiltersView));
  agentViewEl.hidden = !isAgentView;
  sessionsViewEl.hidden = !isSessionsView;
  filtersViewEl.hidden = !isEventFiltersView;
  toolFiltersViewEl.hidden = !isToolFiltersView;
  commandDeckEl.hidden = !isAgentView;
}

function renderControls() {
  const session = latestState.session;
  const pendingCount = latestState.pendingAskUsers.length
    + latestState.pendingPermissions.length
    + latestState.session.pendingMessages.length;
  const displayedActiveContextSize = getDisplayedActiveContextSize();
  sessionCardEl.dataset.state = pendingCount > 0 ? "attention" : session.running ? "running" : "idle";
  contextCardEl.dataset.state = displayedActiveContextSize > 0 ? "tracked" : "empty";
  activeContextSizeValueEl.textContent = formatContextSizeInThousands(displayedActiveContextSize);
  syncSelectOptions(
    modelSelectEl,
    session.supportedModels,
    (model) => model.id,
    (model) => `${model.name} (${model.providerLabel})`
  );
  syncSelectOptions(
    filterProfileSelectEl,
    latestState.filterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );
  syncSelectOptions(
    filterEditorSelectEl,
    latestState.filterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );
  syncSelectOptions(
    toolFilterProfileSelectEl,
    latestState.toolFilterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );
  syncSelectOptions(
    toolFilterEditorSelectEl,
    latestState.toolFilterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );
  syncSelectOptions(
    mcpToolFilterProfileSelectEl,
    latestState.mcpToolFilterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );
  syncSelectOptions(
    mcpToolFilterEditorSelectEl,
    latestState.mcpToolFilterProfiles.profiles,
    (profile) => profile.id,
    (profile) => profile.name
  );

  modelSelectEl.value = session.selectedModelId;
  modelSelectEl.disabled = session.supportedModels.length === 0;
  filterProfileSelectEl.value = latestState.filterProfiles.activeProfileId;
  filterEditorSelectEl.value = selectedFilterEditorProfileId;
  toolFilterProfileSelectEl.value = latestState.toolFilterProfiles.activeProfileId;
  toolFilterEditorSelectEl.value = selectedToolFilterEditorProfileId;
  mcpToolFilterProfileSelectEl.value = latestState.mcpToolFilterProfiles.activeProfileId;
  mcpToolFilterEditorSelectEl.value = selectedMcpToolFilterEditorProfileId;
  clearContextButtonEl.disabled = isClearingContext;
}

function renderSummary(feedItems) {
  const session = latestState.session;
  const currentSession = getCurrentSessionSummary();
  const currentSessionLabel = currentSession
    ? currentSession.label || formatSessionLabel(currentSession.sessionId)
    : "Active session";
  const askCount = latestState.pendingAskUsers.length;
  const permissionCount = latestState.pendingPermissions.length;
  const queuedMessageCount = latestState.session.pendingMessages.length;
  const pendingCount = askCount + permissionCount;

  if (pendingCount > 0) {
    statusHeadlineEl.textContent = "Input required";
    setOptionalText(statusSummaryEl, currentSessionLabel);
    return;
  }

  if (session.running) {
    statusHeadlineEl.textContent = "Agent working";
    setOptionalText(statusSummaryEl, currentSessionLabel);
    return;
  }

  if (queuedMessageCount > 0) {
    statusHeadlineEl.textContent = "Queued messages";
    setOptionalText(statusSummaryEl, currentSessionLabel);
    return;
  }

  if (feedItems.length > 0) {
    statusHeadlineEl.textContent = "Session idle";
    setOptionalText(statusSummaryEl, currentSessionLabel);
    return;
  }

  statusHeadlineEl.textContent = "Ready";
  setOptionalText(statusSummaryEl, currentSessionLabel);
}

function renderSessionList() {
  const sessions = latestState?.sessions || [];
  const currentSession = getCurrentSessionSummary();
  currentSessionLabelEl.textContent = currentSession
    ? currentSession.label || formatSessionLabel(currentSession.sessionId)
    : "Active session";
  createSessionButtonEl.disabled = isSessionMutationLocked();
  createSessionButtonEl.textContent = isMutatingSession ? "Working..." : "New Session";

  sessionListEl.replaceChildren();

  const rootSessions = sessions.filter((session) => session.kind !== "SUB_AGENT");
  const renderedSessionIds = new Set();

  rootSessions.forEach((session) => {
    sessionListEl.append(buildSessionListTree(session, sessions, renderedSessionIds));
  });

  sessions
    .filter((session) => !renderedSessionIds.has(session.sessionId))
    .forEach((session) => {
      sessionListEl.append(buildSessionListTree(session, sessions, renderedSessionIds));
    });
}

function createSessionListButton(session, isSubAgent = false) {
  const row = document.createElement("div");
  row.className = "session-list-row";

  const button = document.createElement("div");
  button.className = "session-list-item";
  button.dataset.state = resolveSessionState(session);
  button.dataset.active = String(session.sessionId === latestState.currentSessionId);
  button.dataset.kind = session.kind;

  const label = document.createElement("strong");
  label.textContent = session.label || formatSessionLabel(session.sessionId);

  const meta = document.createElement("span");
  meta.className = "session-list-meta";
  meta.textContent = formatSessionMeta(session, isSubAgent);

  button.append(label, meta);

  const actions = document.createElement("div");
  actions.className = "session-row-actions";

  const resumeButton = document.createElement("button");
  resumeButton.type = "button";
  resumeButton.className = "session-resume-button";
  resumeButton.textContent = session.sessionId === latestState.currentSessionId ? "Current" : "Resume";
  resumeButton.disabled = isSessionMutationLocked() || session.sessionId === latestState.currentSessionId;
  resumeButton.addEventListener("click", async () => {
    await activateSession(session.sessionId, { openAgentView: true });
  });

  const deleteButton = document.createElement("button");
  deleteButton.type = "button";
  deleteButton.className = "session-delete-button ghost";
  deleteButton.textContent = "Delete";
  deleteButton.disabled = isSessionMutationLocked() || session.running;
  deleteButton.addEventListener("click", async (event) => {
    event.preventDefault();
    event.stopPropagation();
    await deleteSession(session.sessionId);
  });

  actions.append(resumeButton, deleteButton);
  row.append(button, actions);
  return row;
}

function buildSessionListTree(session, sessions, renderedSessionIds) {
  renderedSessionIds.add(session.sessionId);

  const group = document.createElement("div");
  group.className = "session-group";
  group.append(createSessionListButton(session, session.kind === "SUB_AGENT"));

  const childSessions = sessions.filter((candidate) => candidate.parentSessionId === session.sessionId);
  if (childSessions.length > 0) {
    const children = document.createElement("div");
    children.className = "session-children";
    childSessions.forEach((child) => {
      children.append(buildSessionListTree(child, sessions, renderedSessionIds));
    });
    group.append(children);
  }

  return group;
}

function buildFeedItems() {
  if (!latestState) return [];

  const items = [];
  const toolGroups = new Map();

  latestState.session.messages.forEach((message) => {
    items.push({
      id: `message-${message.id}`,
      kind: "message",
      orderId: message.id,
      sortWeight: 0,
      timestampMs: message.timestampMs,
      filterCategory: message.role === "user" ? FILTER_CATEGORY.USER_MESSAGE : FILTER_CATEGORY.ASSISTANT_MESSAGE,
      message,
    });
  });

  latestState.session.activity.forEach((event) => {
    if (event.correlationId && (event.type === ACTIVITY_EVENT_TYPE.TOOL_REQUEST || event.type === ACTIVITY_EVENT_TYPE.TOOL_RESULT)) {
      let group = toolGroups.get(event.correlationId);
      if (!group) {
        group = {
          id: `tool-${event.correlationId}`,
          kind: "tool-group",
          orderId: event.id,
          sortWeight: 1,
          timestampMs: event.timestampMs,
          filterCategory: FILTER_CATEGORY.TOOL,
          request: null,
          result: null,
        };
        toolGroups.set(event.correlationId, group);
        items.push(group);
      }

      if (event.type === ACTIVITY_EVENT_TYPE.TOOL_REQUEST) {
        group.request = event;
      } else {
        group.result = event;
      }

      if (event.timestampMs > group.timestampMs) {
        group.timestampMs = event.timestampMs;
      }
      if (event.id < group.orderId) {
        group.orderId = event.id;
      }
    } else {
      items.push({
        id: `event-${event.id}`,
        kind: "event",
        orderId: event.id,
        sortWeight: 1,
        timestampMs: event.timestampMs,
        filterCategory: resolveActivityFilterCategory(event.type),
        event,
      });
    }
  });

  latestState.pendingAskUsers.forEach((ask) => {
    items.push({
      id: `ask-${ask.id}`,
      kind: "ask",
      sortWeight: 2,
      timestampMs: ask.createdAtMs,
      filterCategory: FILTER_CATEGORY.ASK,
      ask,
    });
  });

  latestState.pendingPermissions.forEach((permission) => {
    items.push({
      id: `permission-${permission.id}`,
      kind: "permission",
      sortWeight: 3,
      timestampMs: permission.createdAtMs,
      filterCategory: FILTER_CATEGORY.PERMISSION,
      permission,
    });
  });

  latestState.session.pendingMessages.forEach((pendingMessage) => {
    items.push({
      id: `pending-message-${pendingMessage.id}`,
      kind: "pending-message",
      sortWeight: 2,
      timestampMs: pendingMessage.createdAtMs,
      filterCategory: FILTER_CATEGORY.USER_MESSAGE,
      pendingMessage,
    });
  });

  const sortedItems = items.sort((left, right) => {
    if (left.timestampMs !== right.timestampMs) {
      return left.timestampMs - right.timestampMs;
    }
    if (typeof left.orderId === "number" && typeof right.orderId === "number" && left.orderId !== right.orderId) {
      return left.orderId - right.orderId;
    }
    if (left.sortWeight !== right.sortWeight) {
      return left.sortWeight - right.sortWeight;
    }
    return String(left.id).localeCompare(String(right.id));
  });

  if (latestState.session.running) {
    const latestTimestampMs = sortedItems.length
      ? sortedItems[sortedItems.length - 1].timestampMs
      : Date.now();
    sortedItems.push({
      id: "transient-working",
      kind: "transient-status",
      orderId: Number.MAX_SAFE_INTEGER,
      sortWeight: 99,
      timestampMs: latestTimestampMs + 1,
      filterCategory: FILTER_CATEGORY.CONTROL,
      title: "Working...",
    });
  }

  return sortedItems;
}

function applyActiveFilter(feedItems) {
  const profile = getActiveFilterProfile();
  const rules = profile?.rules || {};
  return feedItems.flatMap((item) => {
    const displayMode = resolveDisplayMode(item, rules);
    if (displayMode === "HIDDEN") {
      return [];
    }
    return [{ ...item, displayMode }];
  });
}

function resolveDisplayMode(item, rules) {
  if (item.kind === "transient-status") {
    return "FULL";
  }

  if (item.kind === "ask" || item.kind === "permission" || item.kind === "pending-message") {
    return "FULL";
  }

  const candidate = rules[item.filterCategory];
  if (DISPLAY_MODES.includes(candidate)) {
    return candidate;
  }
  return "FULL";
}

function renderStream(feedItems, visibleFeedItems) {
  const shouldStickToBottom = streamShouldStickToBottom();
  streamEl.replaceChildren();

  if (!feedItems.length) {
    streamEl.appendChild(createEmptyState(
      "No session activity yet.",
      "Messages, tool activity, questions, and permissions will appear here in chronological order."
    ));
    return;
  }

  if (!visibleFeedItems.length) {
    streamEl.appendChild(createEmptyState(
      "No activity matches this profile.",
      "Switch to another event filter profile or update the active one from the Event Filters tab."
    ));
    return;
  }

  const fragment = document.createDocumentFragment();
  visibleFeedItems.forEach((item) => {
    fragment.appendChild(renderFeedItem(item));
  });
  streamEl.appendChild(fragment);

  if (shouldStickToBottom) {
    window.requestAnimationFrame(() => {
      streamEl.scrollTop = streamEl.scrollHeight;
    });
  }
}

function renderFeedItem(item) {
  const node = feedItemTemplate.content.firstElementChild.cloneNode(true);
  const markerEl = node.querySelector(".feed-marker");
  const cardEl = node.querySelector(".feed-card");

  node.classList.add(item.kind);
  if (item.kind === "message") {
    node.classList.add(item.message.role === "user" ? "role-user" : "role-assistant");
  } else if (item.kind === "tool-group") {
    node.classList.add(item.request && !item.result ? "type-tool-request" : "type-tool-result");
  } else if (item.kind === "transient-status") {
    node.classList.add("type-working");
  } else if (item.kind === "event") {
    node.classList.add(`type-${item.event.type || "generic"}`);
  }

  if (item.kind === "message") {
    renderMessageItem(cardEl, item);
    configureMarker(markerEl, item, true);
    return node;
  }

  if (item.kind === "event") {
    const isCompact = item.filterCategory === FILTER_CATEGORY.SYSTEM || item.filterCategory === FILTER_CATEGORY.CONTROL;
    renderEventItem(cardEl, item, isCompact);
    configureMarker(markerEl, item, !isCompact);
    return node;
  }

  if (item.kind === "tool-group") {
    renderToolGroupItem(cardEl, item);
    configureMarker(markerEl, item, true);
    return node;
  }

  if (item.kind === "transient-status") {
    renderCompactFeedItem(cardEl, item.title, item.timestampMs);
    configureMarker(markerEl, item, false);
    return node;
  }

  if (item.kind === "ask") {
    renderStaticFeedItem(
      cardEl,
      "Agent question",
      item.timestampMs,
      [createCopyBlock(item.ask.question), renderAskActions(item.ask)]
    );
    configureMarker(markerEl, item, false);
    return node;
  }

  if (item.kind === "pending-message") {
    renderStaticFeedItem(
      cardEl,
      "Queued message",
      item.timestampMs,
      [createCopyBlock(item.pendingMessage.text), renderPendingMessageActions(item.pendingMessage)]
    );
    configureMarker(markerEl, item, false);
    return node;
  }

  renderStaticFeedItem(
    cardEl,
    `${formatLabel(item.permission.accessType)} access requested`,
    item.timestampMs,
    [
      createPathCard("Requested path", item.permission.path),
      renderPermissionActions(item.permission),
    ]
  );
  configureMarker(markerEl, item, false);
  return node;
}

function renderMessageItem(cardEl, item) {
  const title = item.message.role === "user" ? "You" : formatLabel(item.message.role);
  const expanded = isItemExpanded(item);
  renderFoldableFeedItem(
    cardEl,
    createFeedHeader(title, item.timestampMs),
    [createCopyBlock(item.message.text)],
    expanded
  );
}

function renderEventItem(cardEl, item, isCompact) {
  if (isCompact) {
    renderCompactFeedItem(cardEl, item.event.title, item.timestampMs);
    return;
  }

  const expanded = isItemExpanded(item);
  renderFoldableFeedItem(
    cardEl,
    createFeedHeader(item.event.title, item.timestampMs),
    [createDetailBlock(item.event.detail || "No detail provided.")],
    expanded
  );
}

function renderToolGroupItem(cardEl, item) {
  const blocks = [];
  if (item.request) {
    blocks.push(createSubLabel("Request parameters"));
    blocks.push(createJsonAwareDetailBlock(item.request.detail || "No detail provided."));
  }
  if (item.result) {
    blocks.push(createSubLabel("Result output"));
    blocks.push(createDetailBlock(item.result.detail || "No detail provided."));
  }

  const title = formatToolGroupTitle(item);
  const status = resolveToolGroupStatus(item);
  const meta = resolveToolGroupMeta(item);
  const expanded = isItemExpanded(item);
  renderFoldableFeedItem(
    cardEl,
    createToolHeader(title, item.timestampMs, status, meta),
    blocks,
    expanded
  );
}

function formatToolGroupTitle(item) {
  return item.request?.title || item.result?.title || "Tool used: unknown";
}

function renderFoldableFeedItem(cardEl, headerNode, bodyNodes, expanded) {
  cardEl.replaceChildren();
  cardEl.classList.toggle("is-collapsed", !expanded);

  cardEl.appendChild(headerNode);

  if (!expanded) {
    return;
  }

  const body = document.createElement("div");
  body.className = "feed-body";
  bodyNodes.forEach((node) => body.appendChild(node));
  cardEl.appendChild(body);
}

function renderCompactFeedItem(cardEl, title, timestampMs) {
  cardEl.replaceChildren();
  cardEl.classList.add("is-compact");
  cardEl.appendChild(createFeedHeader(title, timestampMs));
}

function renderStaticFeedItem(cardEl, title, timestampMs, bodyNodes) {
  cardEl.replaceChildren();
  cardEl.classList.remove("is-compact");
  cardEl.appendChild(createFeedHeader(title, timestampMs));

  const body = document.createElement("div");
  body.className = "feed-body";
  bodyNodes.forEach((node) => body.appendChild(node));
  cardEl.appendChild(body);
}

function configureMarker(markerEl, item, isFoldable) {
  markerEl.classList.toggle("is-foldable", isFoldable);
  markerEl.classList.toggle("is-static", !isFoldable);
  markerEl.disabled = !isFoldable;

  if (!isFoldable) {
    markerEl.dataset.expanded = "static";
    markerEl.setAttribute("aria-expanded", "true");
    return;
  }

  const expanded = isItemExpanded(item);
  markerEl.dataset.expanded = String(expanded);
  markerEl.setAttribute("aria-expanded", String(expanded));
  markerEl.addEventListener("click", () => {
    feedExpansionState.set(item.id, !expanded);
    render();
  });
}

function renderFilterView() {
  const selectedProfile = getSelectedFilterEditorProfile();
  if (!selectedProfile) {
    return;
  }

  const draft = getFilterProfileDraft(selectedProfile);
  const isBuiltIn = selectedProfile.isBuiltIn;
  const hasChanges = hasFilterProfileChanges(selectedProfile, draft);

  filterEditorHeadlineEl.textContent = selectedProfile.name;
  setOptionalText(
    filterEditorHintEl,
    isBuiltIn
      ? "Built-in profiles cannot be edited. Duplicate one to create a custom starting point."
      : "Custom profiles are stored locally and can be selected from the agent view."
  );

  filterProfileNameInputEl.value = draft.name;
  filterProfileNameInputEl.disabled = isBuiltIn;
  saveFilterProfileButtonEl.disabled = isBuiltIn || !hasChanges;
  deleteFilterProfileButtonEl.disabled = isBuiltIn;
  createFilterProfileButtonEl.disabled = !newFilterProfileName.trim();

  filterRuleListEl.replaceChildren();
  FILTER_CATEGORY_META.forEach((meta) => {
    const rule = document.createElement("article");
    rule.className = "filter-rule";

    const copy = document.createElement("div");
    copy.className = "filter-rule-copy";

    const title = document.createElement("strong");
    title.textContent = meta.label;

    const description = document.createElement("p");
    description.textContent = meta.description;

    copy.append(title, description);

    const controls = document.createElement("div");
    controls.className = "filter-rule-controls";

    DISPLAY_MODES.forEach((mode) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = draft.rules[meta.key] === mode ? "mode-button is-active" : "mode-button ghost";
      button.textContent = formatModeLabel(mode);
      button.disabled = isBuiltIn;
      button.addEventListener("click", () => {
        draft.rules[meta.key] = mode;
        renderFilterView();
      });
      controls.appendChild(button);
    });

    rule.append(copy, controls);
    filterRuleListEl.appendChild(rule);
  });
}

function renderToolFilterView() {
  renderBuiltInToolFilterView();
  renderMcpToolFilterView();
}

function renderBuiltInToolFilterView() {
  const selectedProfile = getSelectedToolFilterEditorProfile();
  if (!selectedProfile || !latestState?.toolFilterCatalog) {
    return;
  }

  const draft = getToolFilterProfileDraft(selectedProfile);
  const isBuiltIn = selectedProfile.isBuiltIn;
  const hasChanges = hasToolFilterProfileChanges(selectedProfile, draft);
  const catalog = latestState.toolFilterCatalog;

  toolFilterEditorHeadlineEl.textContent = selectedProfile.name;
  setOptionalText(
    toolFilterEditorHintEl,
    isBuiltIn
      ? "Built-in profiles cannot be edited. Duplicate one to create a custom starting point."
      : "Custom profiles are stored locally and control which built-in tools the agent may call."
  );

  toolFilterProfileNameInputEl.value = draft.name;
  toolFilterProfileNameInputEl.disabled = isBuiltIn;
  saveToolFilterProfileButtonEl.disabled = isBuiltIn || !hasChanges;
  deleteToolFilterProfileButtonEl.disabled = isBuiltIn;
  createToolFilterProfileButtonEl.disabled = !newToolFilterProfileName.trim();

  toolFilterRuleListEl.replaceChildren();
  catalog.groups.forEach((group) => {
    toolFilterRuleListEl.appendChild(
      renderToolRuleSection({
        ruleTargetIds: getToolRuleTargetIdsForGroup(group.id),
        title: group.label,
        description: group.description,
        draft,
        isBuiltIn,
        allowedModes: TOOL_FILTER_GROUP_MODES,
      })
    );

    const subgroups = catalog.subgroups.filter((subgroup) => subgroup.groupId === group.id);
    subgroups.forEach((subgroup) => {
      toolFilterRuleListEl.appendChild(
        renderToolRuleSection({
          ruleTargetIds: getToolRuleTargetIdsForSubgroup(subgroup.id),
          title: `${group.label} / ${subgroup.label}`,
          description: subgroup.description,
          draft,
          isBuiltIn,
          isNested: true,
          allowedModes: TOOL_FILTER_GROUP_MODES,
        })
      );

      const tools = catalog.tools.filter((tool) => tool.subgroupId === subgroup.id);
      tools.forEach((tool) => {
        toolFilterRuleListEl.appendChild(
          renderToolRuleSection({
            ruleTargetIds: [`tool:${tool.id}`],
            title: tool.label,
            description: `${tool.description} Tool name: ${tool.toolName}.`,
            draft,
            isBuiltIn,
            isNested: true,
            isTool: true,
            allowedModes: TOOL_FILTER_TOOL_MODES,
          })
        );
      });
    });

    const grouplessTools = catalog.tools.filter((tool) => tool.groupId === group.id && !tool.subgroupId);
    grouplessTools.forEach((tool) => {
      toolFilterRuleListEl.appendChild(
        renderToolRuleSection({
          ruleTargetIds: [`tool:${tool.id}`],
          title: tool.label,
          description: `${tool.description} Tool name: ${tool.toolName}.`,
          draft,
          isBuiltIn,
          isNested: true,
          isTool: true,
          allowedModes: TOOL_FILTER_TOOL_MODES,
        })
      );
    });
  });
}

function renderMcpToolFilterView() {
  const selectedProfile = getSelectedMcpToolFilterEditorProfile();
  if (!selectedProfile || !latestState?.mcpToolFilterCatalog) {
    return;
  }

  const draft = getMcpToolFilterProfileDraft(selectedProfile);
  const isBuiltIn = selectedProfile.isBuiltIn;
  const hasChanges = hasMcpToolFilterProfileChanges(selectedProfile, draft);
  const catalog = latestState.mcpToolFilterCatalog;

  mcpToolFilterEditorHeadlineEl.textContent = selectedProfile.name;
  setOptionalText(
    mcpToolFilterEditorHintEl,
    isBuiltIn
      ? "Built-in profiles cannot be edited. Duplicate one to create a custom MCP starting point."
      : "Custom profiles are stored locally and control which MCP server tools the agent may call."
  );

  mcpToolFilterProfileNameInputEl.value = draft.name;
  mcpToolFilterProfileNameInputEl.disabled = isBuiltIn;
  saveMcpToolFilterProfileButtonEl.disabled = isBuiltIn || !hasChanges;
  deleteMcpToolFilterProfileButtonEl.disabled = isBuiltIn;
  createMcpToolFilterProfileButtonEl.disabled = !newMcpToolFilterProfileName.trim();

  mcpToolFilterRuleListEl.replaceChildren();
  if (!catalog.servers.length) {
    mcpToolFilterRuleListEl.appendChild(createEmptyFilterRule("No MCP servers configured.", "Add MCP servers to the config to filter their tools here."));
    return;
  }

  catalog.servers.forEach((server) => {
    const serverTools = catalog.tools.filter((tool) => tool.serverId === server.id);
    const descriptionParts = [server.description || "MCP server"];
    if (!server.isAvailable && server.error) {
      descriptionParts.push(`Unavailable: ${server.error}`);
    }
    if (!serverTools.length) {
      descriptionParts.push("No tools were discovered for this server.");
    }

    mcpToolFilterRuleListEl.appendChild(
      renderToolRuleSection({
        ruleTargetIds: getMcpRuleTargetIdsForServer(server.id),
        title: server.label,
        description: descriptionParts.join(" "),
        draft,
        isBuiltIn,
        allowedModes: TOOL_FILTER_GROUP_MODES,
      })
    );

    serverTools.forEach((tool) => {
      mcpToolFilterRuleListEl.appendChild(
        renderToolRuleSection({
          ruleTargetIds: [tool.id],
          title: tool.label,
          description: `${tool.description} Tool name: ${tool.toolName}.`,
          draft,
          isBuiltIn,
          isNested: true,
          isTool: true,
          allowedModes: TOOL_FILTER_TOOL_MODES,
        })
      );
    });
  });
}

function createEmptyFilterRule(title, description) {
  const rule = document.createElement("article");
  rule.className = "filter-rule";

  const copy = document.createElement("div");
  copy.className = "filter-rule-copy";

  const titleEl = document.createElement("strong");
  titleEl.textContent = title;

  const descriptionEl = document.createElement("p");
  descriptionEl.textContent = description;

  copy.append(titleEl, descriptionEl);
  rule.append(copy);
  return rule;
}

function renderToolRuleSection({ ruleTargetIds, title, description, draft, isBuiltIn, isNested = false, isTool = false, allowedModes }) {
  const rule = document.createElement("article");
  rule.className = "filter-rule tool-filter-rule";
  if (isNested) {
    rule.classList.add("tool-filter-rule-nested");
  }
  if (isTool) {
    rule.classList.add("tool-filter-rule-tool");
  }

  const copy = document.createElement("div");
  copy.className = "filter-rule-copy";

  const titleEl = document.createElement("strong");
  titleEl.textContent = title;

  const descriptionEl = document.createElement("p");
  descriptionEl.textContent = description;

  copy.append(titleEl, descriptionEl);

  const controls = document.createElement("div");
  controls.className = "filter-rule-controls";
  const currentMode = isTool
    ? resolveIndividualToolFilterMode(ruleTargetIds[0], draft.rules)
    : resolveBulkToolFilterMode(ruleTargetIds, draft.rules);

  allowedModes.forEach((mode) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = currentMode === mode ? "mode-button is-active" : "mode-button ghost";
    button.textContent = formatToolFilterModeLabel(mode);
    button.disabled = isBuiltIn || (!isTool && mode === "CUSTOM");
    button.addEventListener("click", () => {
      if (mode === "CUSTOM") {
        return;
      }
      applyToolFilterMode(ruleTargetIds, draft.rules, mode);
      renderToolFilterView();
    });
    controls.appendChild(button);
  });

  rule.append(copy, controls);
  return rule;
}

/** @param {PendingAsk} ask */
function renderAskActions(ask) {
  const wrapper = document.createElement("div");
  wrapper.className = "feed-stack";
  const draft = getAskDraft(ask.id);

  const optionLabel = document.createElement("div");
  optionLabel.className = "section-label";
  optionLabel.textContent = "Quick replies";

  const optionList = document.createElement("div");
  optionList.className = "option-list";
  ask.options.forEach((option, index) => {
    const button = document.createElement("button");
    button.className = "ghost";
    button.textContent = option;
    button.addEventListener("click", async () => {
      askDrafts.delete(ask.id);
      await requestJson(`/api/asks/${ask.id}`, {
        method: HTTP_METHOD.POST,
        body: JSON.stringify({
          isAccepted: true,
          selection: option,
          selectionIndex: index,
        }),
      });
      await loadState();
    });
    optionList.appendChild(button);
  });

  const customLabel = document.createElement("div");
  customLabel.className = "section-label";
  customLabel.textContent = "Custom reply";

  const customBox = document.createElement("div");
  customBox.className = "inline-compose";

  const textarea = document.createElement("textarea");
  textarea.rows = 3;
  textarea.placeholder = "Say what should happen instead.";
  textarea.value = draft.customReply;
  textarea.addEventListener("input", () => {
    draft.customReply = textarea.value;
  });

  const customButton = document.createElement("button");
  customButton.textContent = "Send custom reply";
  customButton.addEventListener("click", async () => {
    const selection = textarea.value.trim();
    if (!selection) return;
    askDrafts.delete(ask.id);
    await requestJson(`/api/asks/${ask.id}`, {
        method: HTTP_METHOD.POST,
      body: JSON.stringify({
        isAccepted: false,
        selection,
        selectionIndex: ask.options.length,
      }),
    });
    await loadState();
  });

  customBox.append(textarea, customButton);
  wrapper.append(optionLabel, optionList, customLabel, customBox);
  return wrapper;
}

/** @param {PendingPermission} permission */
function renderPermissionActions(permission) {
  const wrapper = document.createElement("div");
  wrapper.className = "decision-form";
  const draft = getPermissionDraft(permission);
  const scopeOptions = getPermissionScopeOptions(permission);
  const scopeIndex = clamp(draft.scopeIndex, 0, Math.max(scopeOptions.length - 1, 0));
  draft.scopeIndex = scopeIndex;
  draft.scope = scopeOptions[scopeIndex] || permission.path;

  const note = document.createElement("div");
  note.className = "feed-note";
  note.textContent = "Choose the path scope, then set lifetime and allow or deny the request.";

  const scopeControl = createScopeControl(permission, draft, scopeOptions);

  const lifetimeSelect = document.createElement("select");
  [POLICY_LIFETIME.ONCE, POLICY_LIFETIME.SESSION, POLICY_LIFETIME.FOREVER].forEach((lifetime) => {
    const option = document.createElement("option");
    option.value = lifetime;
    option.textContent = formatLabel(lifetime);
    lifetimeSelect.appendChild(option);
  });
  lifetimeSelect.value = draft.lifetime;
  lifetimeSelect.addEventListener("change", () => {
    draft.lifetime = lifetimeSelect.value;
  });

  const reason = document.createElement("textarea");
  reason.rows = 3;
  reason.placeholder = "Reason, especially useful when denying.";
  reason.value = draft.reason;
  reason.addEventListener("input", () => {
    draft.reason = reason.value;
  });

  const fieldGrid = document.createElement("div");
  fieldGrid.className = "field-grid";
  fieldGrid.append(
    createField("Scope", scopeControl),
    createField("Lifetime", lifetimeSelect)
  );

  const reasonField = createField("Reason", reason);

  const actions = document.createElement("div");
  actions.className = "decision-buttons";

  const allowButton = document.createElement("button");
  allowButton.textContent = "Allow";
  allowButton.addEventListener("click", async () => {
    permissionDrafts.delete(permission.id);
    await submitPermission(permission.id, true, lifetimeSelect.value, draft.scope, reason.value.trim());
  });

  const denyButton = document.createElement("button");
  denyButton.className = "ghost";
  denyButton.textContent = "Deny";
  denyButton.addEventListener("click", async () => {
    permissionDrafts.delete(permission.id);
    await submitPermission(permission.id, false, lifetimeSelect.value, draft.scope, reason.value.trim());
  });

  actions.append(allowButton, denyButton);
  wrapper.append(note, fieldGrid, reasonField, actions);
  return wrapper;
}

/** @param {PendingMessage} pendingMessage */
function renderPendingMessageActions(pendingMessage) {
  const wrapper = document.createElement("div");
  wrapper.className = "feed-stack";

  const note = document.createElement("div");
  note.className = "feed-note";
  note.textContent = "This message will be sent automatically at the next break in the current run.";

  const actions = document.createElement("div");
  actions.className = "decision-buttons";

  const cancelButton = document.createElement("button");
  cancelButton.className = "ghost";
  cancelButton.textContent = "Cancel queued message";
  cancelButton.addEventListener("click", async () => {
    await requestJson(`/api/message/${pendingMessage.id}/cancel`, {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({}),
    });
    await loadState();
  });

  actions.append(cancelButton);
  wrapper.append(note, actions);
  return wrapper;
}

async function submitPermission(id, isAllowed, lifetime, scope, reason) {
  await requestJson(`/api/permissions/${id}`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ isAllowed, lifetime, scope, reason }),
  });
  await loadState();
}

async function submitMessage() {
  const text = messageInputEl.value.trim();
  if (!text) {
    setComposerStatus("error", "Message cannot be empty.");
    return;
  }

  if (isSubmittingMessage) {
    return;
  }

  isSubmittingMessage = true;
  renderComposer();

  try {
    const response = await requestJson("/api/message", {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({ text }),
    });

    if (!response.ok) {
      setComposerStatus("error", response.message || "Message was not accepted.");
      return;
    }

    const wasRunning = Boolean(latestState?.session?.running);
    messageInputEl.value = "";
    autoResizeComposer();
    setComposerStatus("success", response.message || (wasRunning ? "Message queued." : "Message sent."), 1800);
    await loadState();
  } catch (error) {
    setComposerStatus("error", error.message || "Unable to send message.");
  } finally {
    isSubmittingMessage = false;
    renderComposer();
  }
}

agentTabButtonEl.addEventListener("click", () => {
  activeView = "agent";
  render();
});

sessionsTabButtonEl.addEventListener("click", () => {
  activeView = "sessions";
  render();
});

filtersTabButtonEl.addEventListener("click", () => {
  activeView = "filters";
  render();
});

toolFiltersTabButtonEl.addEventListener("click", () => {
  activeView = "tool-filters";
  render();
});

composerEl.addEventListener("submit", async (event) => {
  event.preventDefault();
  await submitMessage();
});

filterProfileSelectEl.addEventListener("change", async () => {
  await requestJson("/api/filter-profiles/select", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ profileId: filterProfileSelectEl.value }),
  });
  await loadState();
});

toolFilterProfileSelectEl.addEventListener("change", async () => {
  await requestJson("/api/tool-filter-profiles/select", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ profileId: toolFilterProfileSelectEl.value }),
  });
  await loadState();
});

mcpToolFilterProfileSelectEl.addEventListener("change", async () => {
  await requestJson("/api/mcp-tool-filter-profiles/select", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ profileId: mcpToolFilterProfileSelectEl.value }),
  });
  await loadState();
});

modelSelectEl.addEventListener("change", async () => {
  await requestJson("/api/model", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({ model: modelSelectEl.value }),
  });
  await loadState();
});

clearContextButtonEl.addEventListener("click", async () => {
  if (isClearingContext) {
    return;
  }

  isClearingContext = true;
  renderControls();

  try {
    const response = await requestJson("/api/context/clear", {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({}),
    });
    lastKnownActiveContextSize = 0;
    setComposerStatus("success", response.message || "Context cleared.", 1800);
    await loadState();
  } catch (error) {
    setComposerStatus("error", error.message || "Unable to clear context.");
  } finally {
    isClearingContext = false;
    renderControls();
  }
});

createSessionButtonEl.addEventListener("click", async () => {
  await createSession();
});

filterEditorSelectEl.addEventListener("change", () => {
  selectedFilterEditorProfileId = filterEditorSelectEl.value;
  renderFilterView();
});

toolFilterEditorSelectEl.addEventListener("change", () => {
  selectedToolFilterEditorProfileId = toolFilterEditorSelectEl.value;
  renderToolFilterView();
});

mcpToolFilterEditorSelectEl.addEventListener("change", () => {
  selectedMcpToolFilterEditorProfileId = mcpToolFilterEditorSelectEl.value;
  renderToolFilterView();
});

newFilterProfileNameInputEl.addEventListener("input", () => {
  newFilterProfileName = newFilterProfileNameInputEl.value;
  renderFilterView();
});

newToolFilterProfileNameInputEl.addEventListener("input", () => {
  newToolFilterProfileName = newToolFilterProfileNameInputEl.value;
  renderToolFilterView();
});

newMcpToolFilterProfileNameInputEl.addEventListener("input", () => {
  newMcpToolFilterProfileName = newMcpToolFilterProfileNameInputEl.value;
  renderToolFilterView();
});

createFilterProfileButtonEl.addEventListener("click", async () => {
  const name = newFilterProfileName.trim();
  if (!name) return;

  const response = await requestJson("/api/filter-profiles", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name,
      baseProfileId: selectedFilterEditorProfileId,
    }),
  });

  if (!response.ok) {
    return;
  }

  newFilterProfileName = "";
  newFilterProfileNameInputEl.value = "";
  selectedFilterEditorProfileId = response.profileId || selectedFilterEditorProfileId;
  await loadState();
});

createToolFilterProfileButtonEl.addEventListener("click", async () => {
  const name = newToolFilterProfileName.trim();
  if (!name) return;

  const response = await requestJson("/api/tool-filter-profiles", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name,
      baseProfileId: selectedToolFilterEditorProfileId,
    }),
  });

  if (!response.ok) {
    return;
  }

  newToolFilterProfileName = "";
  newToolFilterProfileNameInputEl.value = "";
  selectedToolFilterEditorProfileId = response.profileId || selectedToolFilterEditorProfileId;
  await loadState();
});

createMcpToolFilterProfileButtonEl.addEventListener("click", async () => {
  const name = newMcpToolFilterProfileName.trim();
  if (!name) return;

  const response = await requestJson("/api/mcp-tool-filter-profiles", {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name,
      baseProfileId: selectedMcpToolFilterEditorProfileId,
    }),
  });

  if (!response.ok) {
    return;
  }

  newMcpToolFilterProfileName = "";
  newMcpToolFilterProfileNameInputEl.value = "";
  selectedMcpToolFilterEditorProfileId = response.profileId || selectedMcpToolFilterEditorProfileId;
  await loadState();
});

filterProfileNameInputEl.addEventListener("input", () => {
  const profile = getSelectedFilterEditorProfile();
  if (!profile) return;
  const draft = getFilterProfileDraft(profile);
  draft.name = filterProfileNameInputEl.value;
  renderFilterView();
});

toolFilterProfileNameInputEl.addEventListener("input", () => {
  const profile = getSelectedToolFilterEditorProfile();
  if (!profile) return;
  const draft = getToolFilterProfileDraft(profile);
  draft.name = toolFilterProfileNameInputEl.value;
  renderToolFilterView();
});

mcpToolFilterProfileNameInputEl.addEventListener("input", () => {
  const profile = getSelectedMcpToolFilterEditorProfile();
  if (!profile) return;
  const draft = getMcpToolFilterProfileDraft(profile);
  draft.name = mcpToolFilterProfileNameInputEl.value;
  renderToolFilterView();
});

saveFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;
  const draft = getFilterProfileDraft(profile);

  await requestJson(`/api/filter-profiles/${profile.id}`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name: draft.name.trim(),
      rules: draft.rules,
    }),
  });

  filterProfileDrafts.delete(profile.id);
  await loadState();
});

saveToolFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedToolFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;
  const draft = getToolFilterProfileDraft(profile);

  await requestJson(`/api/tool-filter-profiles/${profile.id}`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name: draft.name.trim(),
      rules: draft.rules,
    }),
  });

  toolFilterProfileDrafts.delete(profile.id);
  await loadState();
});

saveMcpToolFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedMcpToolFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;
  const draft = getMcpToolFilterProfileDraft(profile);

  await requestJson(`/api/mcp-tool-filter-profiles/${profile.id}`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({
      name: draft.name.trim(),
      rules: draft.rules,
    }),
  });

  mcpToolFilterProfileDrafts.delete(profile.id);
  await loadState();
});

deleteFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;

  await requestJson(`/api/filter-profiles/${profile.id}/delete`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({}),
  });

  filterProfileDrafts.delete(profile.id);
  await loadState();
});

deleteToolFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedToolFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;

  await requestJson(`/api/tool-filter-profiles/${profile.id}/delete`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({}),
  });

  toolFilterProfileDrafts.delete(profile.id);
  await loadState();
});

deleteMcpToolFilterProfileButtonEl.addEventListener("click", async () => {
  const profile = getSelectedMcpToolFilterEditorProfile();
  if (!profile || profile.isBuiltIn) return;

  await requestJson(`/api/mcp-tool-filter-profiles/${profile.id}/delete`, {
    method: HTTP_METHOD.POST,
    body: JSON.stringify({}),
  });

  mcpToolFilterProfileDrafts.delete(profile.id);
  await loadState();
});

messageInputEl.addEventListener("input", () => {
  autoResizeComposer();
  if (composerStatus.state !== "pending" && composerStatus.text) {
    clearComposerStatus();
    return;
  }
  renderComposer();
});

messageInputEl.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" || event.isComposing) return;
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  composerEl.requestSubmit();
});

function createFeedHeader(title, timestampMs) {
  const header = document.createElement("div");
  header.className = "feed-header";

  const titleEl = document.createElement("p");
  titleEl.className = "feed-title";
  titleEl.textContent = title;

  const timeEl = document.createElement("time");
  timeEl.className = "feed-timestamp";
  timeEl.textContent = formatTimestamp(timestampMs);

  header.append(titleEl, timeEl);
  return header;
}

function createToolHeader(title, timestampMs, status, meta) {
  const header = document.createElement("div");
  header.className = "feed-header tool-header";

  const main = document.createElement("div");
  main.className = "tool-header-main";

  const titleRow = document.createElement("div");
  titleRow.className = "tool-title-row";

  const titleEl = document.createElement("p");
  titleEl.className = "feed-title";
  titleEl.textContent = title;

  titleRow.append(titleEl);

  if (status.kind === "pending") {
    const statusDot = document.createElement("span");
    statusDot.className = "tool-status-dot tool-status-pending";
    statusDot.setAttribute("aria-label", status.label);
    statusDot.title = status.label;
    titleRow.append(statusDot);
  }
  main.append(titleRow);

  if (meta) {
    const metaRow = document.createElement("div");
    metaRow.className = "tool-meta-row";

    const metaLabel = document.createElement("span");
    metaLabel.className = "tool-meta-label";
    metaLabel.textContent = meta.label;

    const metaValue = document.createElement("code");
    metaValue.className = "tool-meta-value";
    metaValue.textContent = meta.value;

    metaRow.append(metaLabel, metaValue);
    main.append(metaRow);
  }

  const timeEl = document.createElement("time");
  timeEl.className = "feed-timestamp";
  timeEl.textContent = formatTimestamp(timestampMs);

  header.append(main, timeEl);
  return header;
}

function createCopyBlock(text) {
  const copy = document.createElement("div");
  copy.className = "feed-copy";
  copy.textContent = text;
  return copy;
}

function createDetailBlock(text) {
  const detail = document.createElement("pre");
  detail.className = "detail-block";
  detail.textContent = text;
  return detail;
}

function createJsonAwareDetailBlock(text) {
  return createDetailBlock(formatJsonIfPossible(text));
}

function resolveToolGroupStatus(item) {
  if (!item.result) {
    return { kind: "pending", label: "In progress" };
  }
  return { kind: "done", label: "" };
}

function resolveToolGroupMeta(item) {
  const request = item.request;
  if (!request?.detail) {
    return null;
  }

  const parsed = parseJsonObjectIfPossible(request.detail);
  if (!parsed || typeof parsed.path !== "string" || !parsed.path.trim()) {
    return null;
  }

  return {
    label: "Path",
    value: parsed.path,
  };
}

function parseJsonObjectIfPossible(text) {
  const trimmed = String(text || "").trim();
  if (!trimmed.startsWith("{")) {
    return null;
  }

  try {
    const parsed = JSON.parse(trimmed);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

function formatJsonIfPossible(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    return text;
  }

  const looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[");
  if (!looksLikeJson) {
    return text;
  }

  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2);
  } catch {
    return text;
  }
}

function createSubLabel(text) {
  const sub = document.createElement("div");
  sub.className = "tool-sub-label";
  sub.textContent = text;
  return sub;
}

function createPathCard(label, path) {
  const card = document.createElement("div");
  card.className = "path-card";

  const labelEl = document.createElement("div");
  labelEl.className = "section-label";
  labelEl.textContent = label;

  const code = document.createElement("code");
  code.textContent = path;

  card.append(labelEl, code);
  return card;
}

function createScopeControl(permission, draft, scopeOptions) {
  const wrapper = document.createElement("div");
  wrapper.className = "scope-control";

  const scopeReadout = document.createElement("code");
  scopeReadout.className = "scope-readout";
  scopeReadout.textContent = draft.scope || permission.path;

  if (scopeOptions.length <= 1) {
    wrapper.classList.add("is-locked");

    const lockedNote = document.createElement("div");
    lockedNote.className = "scope-locked-note";
    lockedNote.textContent = "Scope is fixed for this request.";

    wrapper.append(scopeReadout, lockedNote);
    return wrapper;
  }

  const scopeSlider = document.createElement("input");
  scopeSlider.className = "scope-slider";
  scopeSlider.type = "range";
  scopeSlider.min = "0";
  scopeSlider.max = String(scopeOptions.length - 1);
  scopeSlider.step = "1";
  scopeSlider.value = String(draft.scopeIndex);
  scopeSlider.setAttribute("aria-label", "Permission scope");

  const updateScope = (nextIndex) => {
    const boundedIndex = clamp(nextIndex, 0, scopeOptions.length - 1);
    draft.scopeIndex = boundedIndex;
    draft.scope = scopeOptions[boundedIndex] || permission.path;
    scopeReadout.textContent = draft.scope;
    setRangeFill(scopeSlider, boundedIndex, scopeOptions.length - 1);
  };

  scopeSlider.addEventListener("input", () => {
    updateScope(Number(scopeSlider.value));
  });

  const scopeScale = document.createElement("div");
  scopeScale.className = "scope-scale";

  const rootLabel = document.createElement("span");
  rootLabel.textContent = "Root";

  const leafLabel = document.createElement("span");
  leafLabel.textContent = "Exact path";

  scopeScale.append(rootLabel, leafLabel);
  wrapper.append(scopeReadout, scopeSlider, scopeScale);

  updateScope(draft.scopeIndex);
  return wrapper;
}

function createField(label, control) {
  const field = document.createElement("label");
  field.className = "field";

  const caption = document.createElement("span");
  caption.className = "field-label";
  caption.textContent = label;

  field.append(caption, control);
  return field;
}

function createEmptyState(title, body) {
  const empty = document.createElement("div");
  empty.className = "empty-state";
  empty.innerHTML = `
    <strong>${title}</strong>
    <p>${body}</p>
  `;
  return empty;
}

function getAskDraft(id) {
  if (!askDrafts.has(id)) {
    askDrafts.set(id, { customReply: "" });
  }
  return askDrafts.get(id);
}

function getPermissionDraft(permission) {
  if (!permissionDrafts.has(permission.id)) {
    permissionDrafts.set(permission.id, {
      scopeIndex: Math.max(getPermissionScopeOptions(permission).length - 1, 0),
      scope: permission.path,
      lifetime: POLICY_LIFETIME.ONCE,
      reason: "",
    });
  }
  return permissionDrafts.get(permission.id);
}

function getPermissionScopeOptions(permission) {
  const providedScopes = Array.isArray(permission.scopeOptions)
    ? permission.scopeOptions.filter((scope) => Boolean(scope))
    : [];

  if (!providedScopes.length) {
    return buildPathScopeOptions(permission.path);
  }

  if (providedScopes.length > 1 && providedScopes[0] === permission.path) {
    return providedScopes.slice().reverse();
  }

  return providedScopes;
}

function buildPathScopeOptions(path) {
  const normalized = String(path || "").trim();
  if (!normalized) {
    return [""];
  }

  if (normalized.startsWith("\\\\")) {
    const segments = normalized.split(/[\\/]+/).filter(Boolean);
    if (segments.length < 2) {
      return [normalized];
    }

    const root = `\\\\${segments[0]}\\${segments[1]}`;
    return buildIncrementalScopes(root, segments.slice(2), "\\");
  }

  if (/^[A-Za-z]:[\\/]/.test(normalized)) {
    const segments = normalized.slice(2).split(/[\\/]+/).filter(Boolean);
    return buildIncrementalScopes(`${normalized.slice(0, 2)}\\`, segments, "\\");
  }

  if (normalized.startsWith("/")) {
    const segments = normalized.split("/").filter(Boolean);
    return buildIncrementalScopes("/", segments, "/");
  }

  const segments = normalized.split(/[\\/]+/).filter(Boolean);
  return buildIncrementalScopes("", segments, "/");
}

function buildIncrementalScopes(root, segments, separator) {
  const scopes = [];
  let current = root;

  if (current) {
    scopes.push(current);
  }

  segments.forEach((segment, index) => {
    if (!current) {
      current = segment;
    } else if (current === "/" || current.endsWith(separator)) {
      current = `${current}${segment}`;
    } else {
      current = `${current}${separator}${segment}`;
    }

    if (index === 0 && !root) {
      scopes[0] = current;
    } else {
      scopes.push(current);
    }
  });

  return scopes;
}

function setRangeFill(input, value, max) {
  if (max <= 0) {
    input.style.setProperty("--scope-fill", "100%");
    return;
  }

  const percent = Math.max(0, Math.min(100, (Number(value) / max) * 100));
  input.style.setProperty("--scope-fill", `${percent}%`);
}

function clamp(value, min, max) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return min;
  }
  return Math.min(Math.max(Math.trunc(numeric), min), max);
}

function getFilterProfileDraft(profile) {
  if (!filterProfileDrafts.has(profile.id)) {
    filterProfileDrafts.set(profile.id, {
      name: profile.name,
      rules: { ...profile.rules },
    });
  }
  return filterProfileDrafts.get(profile.id);
}

function getToolFilterProfileDraft(profile) {
  if (!toolFilterProfileDrafts.has(profile.id)) {
    toolFilterProfileDrafts.set(profile.id, {
      name: profile.name,
      rules: { ...profile.rules },
    });
  }
  return toolFilterProfileDrafts.get(profile.id);
}

function getMcpToolFilterProfileDraft(profile) {
  if (!mcpToolFilterProfileDrafts.has(profile.id)) {
    mcpToolFilterProfileDrafts.set(profile.id, {
      name: profile.name,
      rules: { ...profile.rules },
    });
  }
  return mcpToolFilterProfileDrafts.get(profile.id);
}

function syncActiveContextSize(state) {
  const nextActiveContextSize = normalizeActiveContextSize(state?.session?.activeContextSize);
  lastKnownActiveContextSize = nextActiveContextSize;
}

function syncDraftStores() {
  const askIds = new Set((latestState?.pendingAskUsers || []).map((ask) => ask.id));
  const permissionIds = new Set((latestState?.pendingPermissions || []).map((permission) => permission.id));
  const profileIds = new Set((latestState?.filterProfiles?.profiles || []).map((profile) => profile.id));
  const toolProfileIds = new Set((latestState?.toolFilterProfiles?.profiles || []).map((profile) => profile.id));
  const mcpToolProfileIds = new Set((latestState?.mcpToolFilterProfiles?.profiles || []).map((profile) => profile.id));

  askDrafts.forEach((_, id) => {
    if (!askIds.has(id)) {
      askDrafts.delete(id);
    }
  });

  permissionDrafts.forEach((_, id) => {
    if (!permissionIds.has(id)) {
      permissionDrafts.delete(id);
    }
  });

  filterProfileDrafts.forEach((_, id) => {
    if (!profileIds.has(id)) {
      filterProfileDrafts.delete(id);
    }
  });

  toolFilterProfileDrafts.forEach((_, id) => {
    if (!toolProfileIds.has(id)) {
      toolFilterProfileDrafts.delete(id);
    }
  });
  mcpToolFilterProfileDrafts.forEach((_, id) => {
    if (!mcpToolProfileIds.has(id)) {
      mcpToolFilterProfileDrafts.delete(id);
    }
  });

  if (!profileIds.has(selectedFilterEditorProfileId)) {
    selectedFilterEditorProfileId = latestState?.filterProfiles?.activeProfileId || "";
  }
  if (!toolProfileIds.has(selectedToolFilterEditorProfileId)) {
    selectedToolFilterEditorProfileId = latestState?.toolFilterProfiles?.activeProfileId || "";
  }
  if (!mcpToolProfileIds.has(selectedMcpToolFilterEditorProfileId)) {
    selectedMcpToolFilterEditorProfileId = latestState?.mcpToolFilterProfiles?.activeProfileId || "";
  }
}

function syncExpansionState(feedItems) {
  const activeIds = new Set(feedItems.map((item) => item.id));
  feedExpansionState.forEach((_, id) => {
    if (!activeIds.has(id)) {
      feedExpansionState.delete(id);
    }
  });
}

function getActiveFilterProfile() {
  return latestState?.filterProfiles?.profiles?.find(
    (profile) => profile.id === latestState.filterProfiles.activeProfileId
  ) || null;
}

function getSelectedFilterEditorProfile() {
  return latestState?.filterProfiles?.profiles?.find(
    (profile) => profile.id === selectedFilterEditorProfileId
  ) || null;
}

function getSelectedToolFilterEditorProfile() {
  return latestState?.toolFilterProfiles?.profiles?.find(
    (profile) => profile.id === selectedToolFilterEditorProfileId
  ) || null;
}

function getSelectedMcpToolFilterEditorProfile() {
  return latestState?.mcpToolFilterProfiles?.profiles?.find(
    (profile) => profile.id === selectedMcpToolFilterEditorProfileId
  ) || null;
}

function hasFilterProfileChanges(profile, draft) {
  if (draft.name.trim() !== profile.name) {
    return true;
  }

  return FILTER_CATEGORY_META.some((meta) => {
    return (draft.rules[meta.key] || "FULL") !== (profile.rules[meta.key] || "FULL");
  });
}

function hasToolFilterProfileChanges(profile, draft) {
  if (draft.name.trim() !== profile.name) {
    return true;
  }

  const ruleTargetIds = buildToolRuleTargetIds();
  return ruleTargetIds.some((ruleTargetId) => {
    return (draft.rules[ruleTargetId] || "ENABLED") !== (profile.rules[ruleTargetId] || "ENABLED");
  });
}

function hasMcpToolFilterProfileChanges(profile, draft) {
  if (draft.name.trim() !== profile.name) {
    return true;
  }

  const ruleTargetIds = buildMcpRuleTargetIds();
  return ruleTargetIds.some((ruleTargetId) => {
    return (draft.rules[ruleTargetId] || "ENABLED") !== (profile.rules[ruleTargetId] || "ENABLED");
  });
}

function buildToolRuleTargetIds() {
  const catalog = latestState?.toolFilterCatalog;
  if (!catalog) {
    return [];
  }

  return [
    ...catalog.groups.map((group) => `group:${group.id}`),
    ...catalog.subgroups.map((subgroup) => `subgroup:${subgroup.id}`),
    ...catalog.tools.map((tool) => `tool:${tool.id}`),
  ];
}

function buildMcpRuleTargetIds() {
  const catalog = latestState?.mcpToolFilterCatalog;
  if (!catalog) {
    return [];
  }

  return [
    ...catalog.servers.map((server) => `server:${server.id}`),
    ...catalog.tools.map((tool) => tool.id),
  ];
}

function getToolRuleTargetIdsForGroup(groupId) {
  return (latestState?.toolFilterCatalog?.tools || [])
    .filter((tool) => tool.groupId === groupId)
    .map((tool) => `tool:${tool.id}`);
}

function getToolRuleTargetIdsForSubgroup(subgroupId) {
  return (latestState?.toolFilterCatalog?.tools || [])
    .filter((tool) => tool.subgroupId === subgroupId)
    .map((tool) => `tool:${tool.id}`);
}

function getMcpRuleTargetIdsForServer(serverId) {
  return [
    `server:${serverId}`,
    ...(latestState?.mcpToolFilterCatalog?.tools || [])
      .filter((tool) => tool.serverId === serverId)
      .map((tool) => tool.id),
  ];
}

function resolveBulkToolFilterMode(ruleTargetIds, rules) {
  if (!ruleTargetIds.length) {
    return "CUSTOM";
  }

  const values = ruleTargetIds.map((ruleTargetId) => rules[ruleTargetId] || "ENABLED");
  const uniqueValues = Array.from(new Set(values));
  if (uniqueValues.length === 1) {
    return uniqueValues[0];
  }
  return "CUSTOM";
}

function resolveIndividualToolFilterMode(ruleTargetId, rules) {
  return rules[ruleTargetId] || "ENABLED";
}

function applyToolFilterMode(ruleTargetIds, rules, mode) {
  ruleTargetIds.forEach((ruleTargetId) => {
    rules[ruleTargetId] = mode;
  });
}

function isItemExpanded(item) {
  if (feedExpansionState.has(item.id)) {
    return feedExpansionState.get(item.id);
  }
  return item.displayMode !== "MINIFIED";
}

function resolveActivityFilterCategory(type) {
  switch (type) {
    case ACTIVITY_EVENT_TYPE.TOOL_REQUEST:
    case ACTIVITY_EVENT_TYPE.TOOL_RESULT:
      return FILTER_CATEGORY.TOOL;
    case ACTIVITY_EVENT_TYPE.SYSTEM:
    case ACTIVITY_EVENT_TYPE.CONTROL:
    case ACTIVITY_EVENT_TYPE.MODEL:
    case ACTIVITY_EVENT_TYPE.THINKING:
    case ACTIVITY_EVENT_TYPE.ERROR:
    case ACTIVITY_EVENT_TYPE.CUSTOM:
      return type;
    default:
      return FILTER_CATEGORY.CUSTOM;
  }
}

function getDisplayedActiveContextSize() {
  const snapshotValue = normalizeActiveContextSize(latestState?.session?.activeContextSize);
  if (snapshotValue > 0) {
    return snapshotValue;
  }
  return lastKnownActiveContextSize;
}

function normalizeActiveContextSize(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return 0;
  }
  return Math.round(parsed);
}

function syncSelectOptions(selectEl, items, valueFor, labelFor) {
  const currentOptions = Array.from(selectEl.options, (option) => `${option.value}:::${option.textContent}`);
  const nextOptions = items.map((item) => `${valueFor(item)}:::${labelFor(item)}`);
  const didChange =
    currentOptions.length !== nextOptions.length ||
    currentOptions.some((value, index) => value !== nextOptions[index]);

  if (!didChange) {
    return;
  }

  selectEl.innerHTML = "";
  items.forEach((item) => {
    const option = document.createElement("option");
    option.value = valueFor(item);
    option.textContent = labelFor(item);
    selectEl.appendChild(option);
  });
}

function formatTimestamp(timestampMs) {
  return new Date(timestampMs).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function formatLabel(value) {
  return String(value)
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function formatModeLabel(mode) {
  switch (mode) {
    case "FULL":
      return "Full";
    case "MINIFIED":
      return "Mini";
    case "HIDDEN":
      return "Hide";
    default:
      return formatLabel(mode);
  }
}

function formatToolFilterModeLabel(mode) {
  switch (mode) {
    case "ENABLED":
      return "Enabled";
    case "DISABLED":
      return "Disabled";
    case "CUSTOM":
      return "Custom";
    default:
      return formatLabel(mode);
  }
}

function formatContextSizeInThousands(activeContextSize) {
  if (activeContextSize <= 0) {
    return "0k";
  }

  const activeContextSizeInThousands = activeContextSize / 1000;
  return `${activeContextSizeInThousands.toLocaleString(undefined, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}k`;
}

function formatPendingBreakdown(askCount, permissionCount) {
  const parts = [];
  if (askCount > 0) {
    parts.push(pluralize(askCount, "ask"));
  }
  if (permissionCount > 0) {
    parts.push(pluralize(permissionCount, "permission"));
  }
  return parts.join(" and ");
}

function pluralize(count, singular) {
  return `${count} ${count === 1 ? singular : `${singular}s`}`;
}

function setOptionalText(element, text) {
  element.textContent = text;
  element.hidden = !text;
}

function autoResizeComposer() {
  messageInputEl.style.height = "auto";
  messageInputEl.style.height = `${Math.min(messageInputEl.scrollHeight, 200)}px`;
}

function renderComposer() {
  const sessionRunning = Boolean(latestState?.session?.running);
  if (composerStatus.state === "info" && !sessionRunning) {
    composerStatus = { state: "idle", text: "" };
  }
  messageInputEl.disabled = isSubmittingMessage;
  sendButtonEl.disabled = isSubmittingMessage || !messageInputEl.value.trim();
  if (isSubmittingMessage) {
    sendButtonEl.textContent = "Sending...";
    return;
  }
  sendButtonEl.textContent = sessionRunning ? "Queue" : "Send";
}

function getCurrentSessionSummary() {
  return latestState?.sessions?.find((session) => session.sessionId === latestState.currentSessionId) || null;
}

function isSessionMutationLocked() {
  return isMutatingSession || Boolean(latestState?.session?.running);
}

function resolveSessionState(session) {
  if (session.running) {
    return "running";
  }
  if (session.pendingMessageCount > 0) {
    return "attention";
  }
  return "idle";
}

function formatSessionLabel(sessionId) {
  return `Session ${sessionId.slice(0, 8)}`;
}

function formatSessionMeta(session, isSubAgent = false) {
  const countLabel = `${session.messageCount} msg`;
  const prefix = isSubAgent || session.kind === "SUB_AGENT" ? "Sub-agent" : "Root";
  if (session.pendingMessageCount > 0) {
    return `${prefix} · ${countLabel} · ${session.pendingMessageCount} queued`;
  }
  return `${prefix} · ${countLabel}`;
}

async function createSession() {
  if (isSessionMutationLocked()) {
    return;
  }

  isMutatingSession = true;
  renderControls();
  renderSessionList();

  try {
    const response = await requestJson("/api/sessions", {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({}),
    });
    setComposerStatus("success", response.message || "Session created.", 1800);
    await loadState();
  } catch (error) {
    setComposerStatus("error", error.message || "Unable to create session.");
  } finally {
    isMutatingSession = false;
    renderControls();
    renderSessionList();
  }
}

async function activateSession(sessionId, options = {}) {
  if (isSessionMutationLocked()) {
    return;
  }

  isMutatingSession = true;
  renderControls();
  renderSessionList();

  try {
    const response = await requestJson(`/api/sessions/${sessionId}/activate`, {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({}),
    });
    if (options.openAgentView) {
      activeView = "agent";
    }
    setComposerStatus("success", response.message || "Session selected.", 1800);
    await loadState();
  } catch (error) {
    setComposerStatus("error", error.message || "Unable to select session.");
  } finally {
    isMutatingSession = false;
    renderControls();
    renderSessionList();
  }
}

async function deleteSession(sessionId) {
  if (isSessionMutationLocked()) {
    return;
  }

  isMutatingSession = true;
  renderControls();
  renderSessionList();

  try {
    const response = await requestJson(`/api/sessions/${sessionId}/delete`, {
      method: HTTP_METHOD.POST,
      body: JSON.stringify({}),
    });
    setComposerStatus("success", response.message || "Session deleted.", 1800);
    await loadState();
  } catch (error) {
    setComposerStatus("error", error.message || "Unable to delete session.");
  } finally {
    isMutatingSession = false;
    renderControls();
    renderSessionList();
  }
}

function setComposerStatus(state, text, resetAfterMs = 0) {
  clearComposerStatusTimer();
  composerStatus = { state, text };
  renderComposer();

  if (resetAfterMs > 0) {
    composerStatusTimer = window.setTimeout(() => {
      clearComposerStatus();
    }, resetAfterMs);
  }
}

function clearComposerStatus() {
  clearComposerStatusTimer();
  composerStatus = { state: "idle", text: "" };
  renderComposer();
}

function clearComposerStatusTimer() {
  if (composerStatusTimer !== null) {
    window.clearTimeout(composerStatusTimer);
    composerStatusTimer = null;
  }
}

function streamShouldStickToBottom() {
  if (!streamEl.childElementCount) {
    return true;
  }

  const remainingDistance = streamEl.scrollHeight - streamEl.scrollTop - streamEl.clientHeight;
  return remainingDistance < 72;
}

async function boot() {
  autoResizeComposer();
  await loadState();
  window.setInterval(async () => {
    try {
      await loadState();
    } catch (error) {
      console.error(error);
    }
  }, 1200);
}

boot().catch((error) => {
  console.error(error);
});
