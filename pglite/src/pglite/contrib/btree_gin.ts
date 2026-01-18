import type {
  Extension,
  ExtensionSetupResult,
  PGliteInterface,
} from '../interface'

const setup = async (_pg: PGliteInterface, _emscriptenOpts: any) => {
  return {
    bundlePath: new URL('btree_gin.tar.gz', import.meta.url),
  } satisfies ExtensionSetupResult
}

export const btree_gin = {
  name: 'btree_gin',
  setup,
} satisfies Extension
