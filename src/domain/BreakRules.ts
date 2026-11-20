import { BreakConfig } from '../types/models';

export const DEFAULT_BREAK_CONFIG: BreakConfig = {
  enabled: true,
  tasksForShortBreak: 1,
  shortBreakMinutes: 5,
  tasksForLongBreak: 3,
  longBreakMinutes: 15,
};

export interface BreakCredits {
  shortAvailable: number;
  longAvailable: number;
}

export function hasAnyCredits(c: BreakCredits): boolean {
  return c.shortAvailable > 0 || c.longAvailable > 0;
}

export const BreakRules = {
  earned(completedTasks: number, config: BreakConfig): { short: number; long: number } {
    if (!config.enabled || completedTasks <= 0) return { short: 0, long: 0 };
    const s = Math.max(1, config.tasksForShortBreak);
    const l = Math.max(1, config.tasksForLongBreak);
    let short = 0;
    let long = 0;
    for (let k = 1; k <= completedTasks; k++) {
      if (k % l === 0) {
        long++;
      } else if (k % s === 0) {
        short++;
      }
    }
    return { short, long };
  },

  available(
    completedTasks: number,
    shortUsed: number,
    longUsed: number,
    config: BreakConfig,
  ): BreakCredits {
    const { short, long } = BreakRules.earned(completedTasks, config);
    return {
      shortAvailable: Math.max(0, short - shortUsed),
      longAvailable: Math.max(0, long - longUsed),
    };
  },
};
