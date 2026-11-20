export const Class11Chapters = {
  SEED_VERSION: 1,

  builtInSubjects: ['Mathematics', 'Physics', 'Chemistry', 'Biology', 'Other'],

  mathematics: [
    'Sets',
    'Relations and Functions',
    'Trigonometric Functions',
    'Complex Numbers and Quadratic Equations',
    'Linear Inequalities',
    'Permutations and Combinations',
    'Binomial Theorem',
    'Sequences and Series',
    'Straight Lines',
    'Conic Sections',
    'Introduction to Three Dimensional Geometry',
    'Limits and Derivatives',
    'Statistics',
    'Probability',
  ],

  physics: [
    'Units and Measurements',
    'Motion in a Straight Line',
    'Motion in a Plane',
    'Laws of Motion',
    'Work, Energy and Power',
    'System of Particles and Rotational Motion',
    'Gravitation',
    'Mechanical Properties of Solids',
    'Mechanical Properties of Fluids',
    'Thermal Properties of Matter',
    'Thermodynamics',
    'Kinetic Theory',
    'Oscillations',
    'Waves',
  ],

  chemistry: [
    'Some Basic Concepts of Chemistry',
    'Structure of Atom',
    'Classification of Elements and Periodicity in Properties',
    'Chemical Bonding and Molecular Structure',
    'Thermodynamics',
    'Equilibrium',
    'Redox Reactions',
    'Organic Chemistry: Some Basic Principles and Techniques',
    'Hydrocarbons',
  ],

  bySubject: {} as Record<string, string[]>,
};

Class11Chapters.bySubject = {
  Mathematics: Class11Chapters.mathematics,
  Physics: Class11Chapters.physics,
  Chemistry: Class11Chapters.chemistry,
};
