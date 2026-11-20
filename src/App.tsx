import React, { useEffect, useState } from 'react';
import {
  BarChart3,
  CheckSquare,
  Flame,
  Home as HomeIcon,
  Settings,
  Timer,
} from 'lucide-react';
import { useFocusFlow } from './store/FocusFlowStore';
import { SessionClock, sessionToClock } from './domain/SessionClock';
import { formatClock } from './domain/Format';
import { FloatingAssistant } from './components/FloatingAssistant';
import { FocusShieldModal } from './components/FocusShieldModal';
import { OfflineIndicator, PWAInstallButton } from './components/PWAInstallControl';
import { HomeScreen } from './screens/HomeScreen';
import { WidgetsScreen } from './screens/WidgetsScreen';
import { TasksScreen } from './screens/TasksScreen';
import { SubjectsScreen } from './screens/SubjectsScreen';
import { SessionPrepScreen } from './screens/SessionPrepScreen';
import { FocusScreen } from './screens/FocusScreen';
import { StatisticsScreen } from './screens/StatisticsScreen';
import { HistoryScreen } from './screens/HistoryScreen';
import { SessionSummaryScreen } from './screens/SessionSummaryScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { BlockingScreen } from './screens/BlockingScreen';
import { DetectorTestScreen } from './screens/DetectorTestScreen';
import { AiSettingsScreen } from './screens/AiSettingsScreen';
import { AiProvidersScreen } from './screens/AiProvidersScreen';
import { DataSettingsScreen } from './screens/DataSettingsScreen';

type TabKey = 'home' | 'tasks' | 'focus' | 'statistics' | 'settings';

type Route =
  | { name: 'tab'; tab: TabKey }
  | { name: 'widgets' }
  | { name: 'prepare'; presetId?: number | null }
  | { name: 'subjects' }
  | { name: 'history' }
  | { name: 'summary'; sessionId: number }
  | { name: 'blocking' }
  | { name: 'detector' }
  | { name: 'ai' }
  | { name: 'ai_providers' }
  | { name: 'data' };

const TOP_TABS: Array<{ key: TabKey; label: string; icon: React.FC<{ className?: string }> }> = [
  { key: 'home', label: 'Home', icon: HomeIcon },
  { key: 'tasks', label: 'Tasks', icon: CheckSquare },
  { key: 'focus', label: 'Focus', icon: Timer },
  { key: 'statistics', label: 'Statistics', icon: BarChart3 },
  { key: 'settings', label: 'Settings', icon: Settings },
];

export const App: React.FC = () => {
  const {
    activeSession,
    now,
    lastEndedSessionId,
    clearLastEndedSession,
    lastSnapGptTrigger,
    dismissSnapGptTrigger,
  } = useFocusFlow();
  const [stack, setStack] = useState<Route[]>([{ name: 'tab', tab: 'home' }]);

  const current = stack[stack.length - 1] ?? { name: 'tab', tab: 'home' };

  const push = (route: Route) => setStack((prev) => [...prev, route]);
  const pop = () =>
    setStack((prev) => (prev.length > 1 ? prev.slice(0, -1) : [{ name: 'tab', tab: 'home' }]));
  const switchTab = (tab: TabKey) => setStack([{ name: 'tab', tab }]);

  useEffect(() => {
    if (lastEndedSessionId != null) {
      const id = lastEndedSessionId;
      clearLastEndedSession();
      setStack([{ name: 'tab', tab: 'focus' }, { name: 'summary', sessionId: id }]);
    }
  }, [clearLastEndedSession, lastEndedSessionId]);

  const activeTab: TabKey | null =
    current.name === 'tab'
      ? current.tab
      : current.name === 'widgets'
        ? 'home'
        : current.name === 'subjects'
          ? 'tasks'
          : current.name === 'prepare'
            ? 'focus'
            : current.name === 'history' || current.name === 'summary'
              ? 'statistics'
              : 'settings';

  let activeClockText: string | null = null;
  let onBreak = false;
  if (activeSession) {
    const clock = sessionToClock(activeSession.session);
    onBreak = SessionClock.isOnBreak(clock, now);
    const displayMs = onBreak
      ? SessionClock.breakRemainingMs(clock, now)
      : activeSession.session.mode === 'TIMED'
        ? (SessionClock.remainingMs(clock, now) ?? 0)
        : SessionClock.focusElapsedMs(clock, now);
    activeClockText = formatClock(displayMs);
  }

  const renderScreen = () => {
    switch (current.name) {
      case 'tab':
        switch (current.tab) {
          case 'home':
            return (
              <HomeScreen
                onStartSession={() => push({ name: 'prepare', presetId: null })}
                onOpenFocus={() => switchTab('focus')}
                onOpenSession={(id) => push({ name: 'summary', sessionId: id })}
                onOpenHistory={() => push({ name: 'history' })}
                onEditWidgets={() => push({ name: 'widgets' })}
                onOpenTasks={() => switchTab('tasks')}
                onOpenStatistics={() => switchTab('statistics')}
              />
            );
          case 'tasks':
            return <TasksScreen onManageSubjects={() => push({ name: 'subjects' })} />;
          case 'focus':
            return <FocusScreen onStartSession={() => push({ name: 'prepare', presetId: null })} />;
          case 'statistics':
            return <StatisticsScreen onOpenHistory={() => push({ name: 'history' })} />;
          case 'settings':
            return (
              <SettingsScreen
                onOpenBlocking={() => push({ name: 'blocking' })}
                onOpenAi={() => push({ name: 'ai' })}
                onOpenData={() => push({ name: 'data' })}
              />
            );
        }
      case 'widgets':
        return <WidgetsScreen onBack={pop} />;
      case 'prepare':
        return (
          <SessionPrepScreen
            presetId={current.presetId ?? null}
            onBack={pop}
            onStarted={(opts) => switchTab(opts?.launchMinimized ? 'home' : 'focus')}
            onManageSubjects={() => push({ name: 'subjects' })}
          />
        );
      case 'subjects':
        return <SubjectsScreen onBack={pop} />;
      case 'history':
        return (
          <HistoryScreen
            onBack={pop}
            onOpenSession={(id) => push({ name: 'summary', sessionId: id })}
          />
        );
      case 'summary':
        return (
          <SessionSummaryScreen
            sessionId={current.sessionId}
            onBack={pop}
            onStartNext={() => push({ name: 'prepare', presetId: null })}
          />
        );
      case 'blocking':
        return (
          <BlockingScreen
            onBack={pop}
            onTestDetector={() => push({ name: 'detector' })}
          />
        );
      case 'detector':
        return <DetectorTestScreen onBack={pop} />;
      case 'ai':
        return (
          <AiSettingsScreen
            onBack={pop}
            onOpenProviders={() => push({ name: 'ai_providers' })}
          />
        );
      case 'ai_providers':
        return <AiProvidersScreen onBack={pop} />;
      case 'data':
        return <DataSettingsScreen onBack={pop} />;
    }
  };

  return (
    <div className="min-h-screen bg-[var(--ff-bg)] text-[var(--ff-on-bg)] flex">
      {/* Desktop Left Sidebar */}
      <aside className="hidden md:flex w-60 shrink-0 flex-col justify-between border-r border-[var(--ff-outline)]/25 bg-[var(--ff-surface)] p-4 sticky top-0 h-screen">
        <div className="flex flex-col gap-6">
          <button
            type="button"
            onClick={() => switchTab('home')}
            className="flex items-center gap-2.5 px-2 py-1 text-left cursor-pointer"
          >
            <div className="h-9 w-9 rounded-xl bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)] flex items-center justify-center">
              <Flame className="h-5 w-5" />
            </div>
            <div>
              <div className="text-base font-semibold tracking-tight text-[var(--ff-on-bg)]">
                FocusFlow
              </div>
              <div className="text-[11px] text-[var(--ff-on-surface-variant)]">
                Study workspace
              </div>
            </div>
          </button>

          <nav aria-label="Primary" className="flex flex-col gap-1">
            {TOP_TABS.map(({ key, label, icon: Icon }) => {
              const selected = activeTab === key;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => switchTab(key)}
                  className={`flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-colors cursor-pointer ${
                    selected
                      ? 'bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)]'
                      : 'text-[var(--ff-on-surface-variant)] hover:bg-[var(--ff-surface-variant)] hover:text-[var(--ff-on-bg)]'
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span>{label}</span>
                </button>
              );
            })}
          </nav>
        </div>

        <div className="flex flex-col gap-3">
          <PWAInstallButton />
          {activeSession && activeClockText && (
            <button
              type="button"
              onClick={() => switchTab('focus')}
              className="rounded-2xl border border-[var(--ff-primary)]/40 bg-[var(--ff-primary-container)]/35 p-3.5 text-left hover:bg-[var(--ff-primary-container)]/55 transition-colors cursor-pointer"
            >
              <div className="text-[11px] font-semibold uppercase tracking-wider text-[var(--ff-primary)]">
                {onBreak ? 'On break' : 'In session'}
              </div>
              <div className="text-sm font-medium text-[var(--ff-on-bg)] truncate mt-0.5">
                {activeSession.session.subjectName}
              </div>
              <div className="text-lg font-semibold font-mono-tabular tabular-nums text-[var(--ff-on-bg)] mt-0.5">
                {activeClockText}
              </div>
            </button>
          )}
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 min-w-0 flex flex-col">
        {/* Mobile Top Bar */}
        <header className="md:hidden flex items-center justify-between border-b border-[var(--ff-outline)]/25 bg-[var(--ff-surface)] px-4 py-3 sticky top-0 z-20">
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded-lg bg-[var(--ff-primary-container)] text-[var(--ff-on-primary-container)] flex items-center justify-center">
              <Flame className="h-4 w-4" />
            </div>
            <span className="text-sm font-semibold text-[var(--ff-on-bg)]">FocusFlow</span>
          </div>
          <div className="flex items-center gap-2">
            <PWAInstallButton compact />
            {activeSession && activeClockText && (
              <button
                type="button"
                onClick={() => switchTab('focus')}
                className="text-xs font-mono-tabular tabular-nums font-semibold text-[var(--ff-primary)] cursor-pointer"
              >
                {onBreak ? 'Break ' : ''}
                {activeClockText}
              </button>
            )}
          </div>
        </header>

        <main
          className={`flex-1 w-full max-w-5xl mx-auto px-4 md:px-8 py-5 md:py-7 ${
            current.name === 'tab' ? 'pb-24 md:pb-8' : 'pb-10'
          }`}
        >
          {lastSnapGptTrigger && (
            <div className="mb-4 rounded-xl border border-[var(--ff-primary)]/45 bg-[var(--ff-primary-container)]/35 px-4 py-3 flex items-center justify-between gap-3 text-sm">
              <div className="min-w-0">
                <div className="font-semibold text-[var(--ff-on-bg)]">
                  SnapGPT started automatically
                </div>
                <div className="text-xs text-[var(--ff-on-surface-variant)] truncate">
                  Target app: {lastSnapGptTrigger.packageName} · FocusFlow continues tracking in the
                  floating circle.
                </div>
              </div>
              <button
                type="button"
                onClick={dismissSnapGptTrigger}
                className="text-xs font-medium text-[var(--ff-primary)] hover:underline shrink-0 cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          )}
          {renderScreen()}
        </main>
      </div>

      {/* Mobile Bottom Navigation */}
      {current.name === 'tab' && (
        <nav
          aria-label="Mobile navigation"
          className="md:hidden fixed bottom-0 inset-x-0 z-20 border-t border-[var(--ff-outline)]/25 bg-[var(--ff-surface)] grid grid-cols-5"
        >
          {TOP_TABS.map(({ key, label, icon: Icon }) => {
            const selected = current.tab === key;
            return (
              <button
                key={key}
                type="button"
                onClick={() => switchTab(key)}
                className={`flex flex-col items-center justify-center gap-1 py-2.5 text-[11px] font-medium cursor-pointer ${
                  selected ? 'text-[var(--ff-primary)]' : 'text-[var(--ff-on-surface-variant)]'
                }`}
              >
                <Icon className="h-5 w-5" />
                <span>{label}</span>
              </button>
            );
          })}
        </nav>
      )}

      <FloatingAssistant onOpenFocus={() => switchTab('focus')} />
      <FocusShieldModal />
      <OfflineIndicator />
    </div>
  );
};
