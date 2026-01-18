import type {
  Extension,
  ExtensionSetupResult,
  PGliteInterface,
} from '../interface'

const setup = async (_pg: PGliteInterface, _emscriptenOpts: any) => {
  return {
    bundlePath: new URL('citext.tar.gz', import.meta.url),
  } satisfies ExtensionSetupResult
}

export const citext = {
  name: 'citext',
  setup,
} satisfies Extension
