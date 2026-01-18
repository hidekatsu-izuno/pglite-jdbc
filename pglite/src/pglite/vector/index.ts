import type {
  Extension,
  ExtensionSetupResult,
  PGliteInterface,
} from '../interface'

const setup = async (_pg: PGliteInterface, emscriptenOpts: any) => {
  return {
    emscriptenOpts,
    bundlePath: new URL('vector.tar.gz', import.meta.url),
  } satisfies ExtensionSetupResult
}

export const vector = {
  name: 'pgvector',
  setup,
} satisfies Extension
