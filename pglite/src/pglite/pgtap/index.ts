import type {
  Extension,
  ExtensionSetupResult,
  PGliteInterface,
} from '../interface'

const setup = async (_pg: PGliteInterface, emscriptenOpts: any) => {
  return {
    emscriptenOpts,
    bundlePath: new URL('pgtap.tar.gz', import.meta.url),
  } satisfies ExtensionSetupResult
}

export const pgtap = {
  name: 'pgtap',
  setup,
} satisfies Extension
