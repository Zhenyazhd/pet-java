/** What the "Create CV" button asks the AI. The backend adds the profile's career path and name to every request. */
export const CREATE_FROM_PROFILE_INSTRUCTION = [
  'Write my CV from my profile.',
  'Use only facts from my career path, my name and my email; do not invent employers, dates, degrees or skills.',
  'Replace every placeholder of the current template (such as "YOUR NAME", "you@example.com" and the example jobs)',
  'with my real details. Leave a field or section empty when my profile does not say, and in your reply tell me',
  'what is missing and that I can add it to the Career path on my Profile page.',
  'Keep the structure: header, profile summary, experience with bullets, education, achievements, skills.',
  'Return the full CV with every field present.',
].join(' ')

export const CREATE_FROM_PROFILE_SHOWN_AS = 'Create my CV from my profile.'
export const shouldOfferCreate = (version: number): boolean => version === 0
