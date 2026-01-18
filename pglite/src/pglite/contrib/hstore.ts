import type {
  Extension,
  ExtensionSetupResult,
  PGliteInterface,
} from '../interface'

const setup = async (_pg: PGliteInterface, _emscriptenOpts: any) => {
  return {
    bundlePath: new URL('hstore.tar.gz', import.meta.url),
  } satisfies ExtensionSetupResult
}

export const hstore = {
  name: 'hstore',
  setup,
} satisfies Extension
