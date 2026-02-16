import { runDataPackageBootstrap } from "./parts/data-package-bootstrap.ts";
import { createEnviron } from "./parts/environ.ts";
import { createFdWasiFunctions } from "./parts/fd-wasi.ts";
import { createFSRuntime } from "./parts/fs.ts";
import { createAddrInfoFunctions } from "./parts/getaddrinfo.ts";
import { createInvokeWrappers } from "./parts/invoke-wrappers.ts";
import { PATH } from "./parts/path.ts";
import { PGLITE_DATA_METADATA } from "./parts/pglite-data-metadata.ts";
import {
	createRuntimeHostFsSupport,
	initializeRuntimeEnvironment,
	initializeRuntimeHost,
} from "./parts/runtime-host.ts";
import { createRuntimeUtils } from "./parts/runtime-utils.ts";
import { createSyscallImplementations } from "./parts/syscall-runtime.ts";
import { createSYSCALLS } from "./parts/syscalls.ts";
import {
	intArrayFromString,
	lengthBytesUTF8,
	stringToUTF8Array,
	UTF8ArrayToString,
} from "./parts/text-codec.ts";

const _scriptName = import.meta.url;

type PgliteModule = Record<string, any>;

export default async function (moduleArg: PgliteModule = {}): Promise<PgliteModule> {
	// 1) Bootstrapping and host/environment setup
	const Module: PgliteModule = moduleArg;
	let readyPromiseResolve: (value: PgliteModule | PromiseLike<PgliteModule>) => void;
	let readyPromiseReject: (reason?: any) => void;
	const readyPromise = new Promise((resolve: any, reject: any) => {
		readyPromiseResolve = resolve;
		readyPromiseReject = reject;
	});
	const environment = await initializeRuntimeEnvironment({
		importMetaUrl: _scriptName,
	});
	const ENVIRONMENT_IS_WORKER = environment["ENVIRONMENT_IS_WORKER"];
	const ENVIRONMENT_IS_NODE = environment["ENVIRONMENT_IS_NODE"];
	const require = environment["require"];
	Module["expectedDataFileDownloads"] ??= 0;
	Module["expectedDataFileDownloads"]++;
	runDataPackageBootstrap({ Module, PGLITE_DATA_METADATA, require });
	const moduleOverrides = Object.assign({}, Module);
	const host = await initializeRuntimeHost({
		Module,
		moduleOverrides,
		scriptName: _scriptName,
		importMetaUrl: import.meta.url,
		environment,
	});
	const fs = host["fs"];
	const out = host["out"];
	const err = host["err"];
	const arguments_ = host["arguments_"];
	const thisProgram = host["thisProgram"];
	const quit_ = host["quit_"];
	const locateFile = host["locateFile"];
	const readBinary = host["readBinary"];
	const readAsync =
		host["readAsync"] ??
		(async (filename: any) => {
			if (!readBinary) {
				throw new Error("readAsync is unavailable");
			}
			return readBinary(filename);
		});
	const findWasmBinary = host["findWasmBinary"];
	const isDataURI = host["isDataURI"];
	let dynamicLibraries = Module["dynamicLibraries"] || [];
	const wasmBinary = Module["wasmBinary"];

	// 2) Core wasm runtime state
	let ABORT = false;
	let EXITSTATUS: number | undefined;
	function assert(condition: any, text?: string) {
		if (!condition) {
			abort(text);
		}
	}
	let wasmMemory: WebAssembly.Memory;
	// Predeclare wasm export wrappers to avoid TDZ before lazy assignment
	let _AcquireExternalFD, _AcquireRewriteLocks, _AddWaitEventToSet, _AllocSetContextCreateInternal, _AllocateFile, _ArrayGetIntegerTypmods, _ArrayGetNItems, _BackendXidGetPid, _BackgroundWorkerInitializeConnectionByOid, _BackgroundWorkerUnblockSignals, _BaseBackupAddTarget, _BeginCopyFrom, _BeginInternalSubTransaction, _BlessTupleDesc, _BufferGetBlockNumber, _BufferUsageAccumDiff, _BuildIndexInfo, _BuildTupleFromCStrings, _CacheRegisterSyscacheCallback, _CachedPlanAllowsSimpleValidityCheck, _CachedPlanIsSimplyValid, _CallerFInfoFunctionCall2, _CatalogTupleDelete, _CatalogTupleInsert, _CatalogTupleUpdate, _CheckFunctionValidatorAccess, _CheckIndexCompatible, _CheckTableNotInUse, _CleanQuerytext, _ClosePipeStream, _CloseTransientFile, _CommandCounterIncrement, _CommitTransactionCommand, _ConditionVariableCancelSleep, _ConditionVariableInit, _ConditionVariableSignal, _ConditionVariableSleep, _ConditionalLockBuffer, _ConditionalLockRelationOid, _CopyErrorData, _CopyFromErrorCallback, _CreateDestReceiver, _CreateExecutorState, _CreateExprContext, _CreateParallelContext, _CreateQueryDesc, _CreateTableAsRelExists, _CreateTemplateTupleDesc, _CreateTransientRelDestReceiver, _CreateTrigger, _CreateTupleDescCopy, _DatumGetEOHP, _DecrTupleDescRefCount, _DefineCustomBoolVariable, _DefineCustomEnumVariable, _DefineCustomIntVariable, _DefineCustomRealVariable, _DefineCustomStringVariable, _DefineIndex, _DefineRelation, _DeleteExpandedObject, _DestroyParallelContext, _DirectFunctionCall1Coll, _DirectFunctionCall2Coll, _DirectFunctionCall3Coll, _DirectFunctionCall4Coll, _DirectFunctionCall5Coll, _EnableQueryId, _EndCopyFrom, _EnsurePortalSnapshotExists, _EnterParallelMode, _EvictUnpinnedBuffer, _ExecAsyncRequestDone, _ExecAsyncRequestPending, _ExecAsyncResponse, _ExecDropSingleTupleTableSlot, _ExecFetchSlotHeapTuple, _ExecFindJunkAttributeInTlist, _ExecForceStoreHeapTuple, _ExecGetResultRelCheckAsUser, _ExecGetReturningSlot, _ExecInitExpr, _ExecInitExprList, _ExecInitExprWithParams, _ExecOpenScanRelation, _ExecReScan, _ExecStoreAllNullTuple, _ExecStoreHeapTuple, _ExecStoreVirtualTuple, _ExecuteTruncateGuts, _ExecutorEnd, _ExecutorFinish, _ExecutorRun, _ExecutorStart, _ExitParallelMode, _ExplainBeginOutput, _ExplainEndOutput, _ExplainPrintJITSummary, _ExplainPrintPlan, _ExplainPrintTriggers, _ExplainPropertyInteger, _ExplainPropertyText, _ExplainQueryParameters, _ExplainQueryText, _ExprEvalPushStep, _ExtendBufferedRel, _Float8GetDatum, _FlushErrorState, _FreeAccessStrategy, _FreeCachedExpression, _FreeErrorData, _FreeExecutorState, _FreeExprContext, _FreeFile, _FreeQueryDesc, _FunctionCall0Coll, _FunctionCall1Coll, _FunctionCall2Coll, _FunctionCall4Coll, _GenerationContextCreate, _GenericXLogAbort, _GenericXLogFinish, _GenericXLogRegisterBuffer, _GenericXLogStart, _GetAccessStrategy, _GetActiveSnapshot, _GetCachedExpression, _GetCommandTagName, _GetConfigOption, _GetCurrentCommandId, _GetCurrentSubTransactionId, _GetCurrentTimestamp, _GetCurrentTransactionNestLevel, _GetDatabaseEncoding, _GetDatabaseEncodingName, _GetDefaultOpClass, _GetErrorContextStack, _GetExistingLocalJoinPath, _GetFlushRecPtr, _GetForeignColumnOptions, _GetForeignDataWrapper, _GetForeignServer, _GetForeignServerByName, _GetForeignServerExtended, _GetForeignTable, _GetFreeIndexPage, _GetMultiXactIdMembers, _GetNamedDSMSegment, _GetNamedLWLockTranche, _GetNumRegisteredWaitEvents, _GetOldestNonRemovableTransactionId, _GetRecordedFreeSpace, _GetRunningTransactionData, _GetSearchPathMatcher, _GetSysCacheHashValue, _GetSysCacheOid, _GetTopFullTransactionId, _GetTransactionSnapshot, _GetUserId, _GetUserIdAndSecContext, _GetUserMapping, _GetUserNameFromId, _GetXLogReplayRecPtr, _HeapTupleGetUpdateXid, _HeapTupleHeaderGetDatum, _HeapTupleSatisfiesUpdate, _HeapTupleSatisfiesVacuum, _HeapTupleSatisfiesVisibility, _IncrementVarSublevelsUp, _IndexFreeSpaceMapVacuum, _IndexGetRelation, _InitMaterializedSRF, _InitializeParallelDSM, _InputFunctionCall, _InstrAlloc, _InstrEndLoop, _InstrUpdateTupleCount, _Int64GetDatum, _IsValidJsonNumber, _ItemPointerCompare, _ItemPointerEquals, _JsonbValueToJsonb, _LWLockAcquire, _LWLockInitialize, _LWLockNewTrancheId, _LWLockRegisterTranche, _LWLockRelease, _LaunchParallelWorkers, _LockBufHdr, _LockBuffer, _LockBufferForCleanup, _LockPage, _LockRelationForExtension, _LockRelationOid, _MakeExpandedObjectReadOnlyInternal, _MakePerTupleExprContext, _MakeSingleTupleTableSlot, _MarkBufferDirty, _MarkGUCPrefixReserved, _MemoryContextAlloc, _MemoryContextAllocExtended, _MemoryContextAllocHuge, _MemoryContextAllocZero, _MemoryContextDelete, _MemoryContextDeleteChildren, _MemoryContextGetParent, _MemoryContextMemAllocated, _MemoryContextReset, _MemoryContextSetIdentifier, _MemoryContextSetParent, _MemoryContextStrdup, _MultiXactIdPrecedes, _MultiXactIdPrecedesOrEquals, _NameListToString, _NewExplainState, _NewGUCNestLevel, _NewRelationCreateToastTable, _NextCopyFrom, _OidOutputFunctionCall, _OpenTransientFile, _OpernameGetOprid, _OutputFunctionCall, _OutputPluginPrepareWrite, _OutputPluginWrite, _PageAddItemExtended, _PageGetExactFreeSpace, _PageGetFreeSpace, _PageGetHeapFreeSpace, _PageIndexMultiDelete, _PageIndexTupleOverwrite, _PageInit, _ParseFuncOrColumn, _PinPortal, _PopActiveSnapshot, _PrefetchBuffer, _ProcessConfigFile, _ProcessCopyOptions, _ProcessInterrupts, _PushActiveSnapshot, _PushCopiedSnapshot, _QueryRewrite, _RangeVarCallbackMaintainsTable, _RangeVarGetRelidExtended, _ReThrowError, _ReadBuffer, _ReadBufferExtended, _ReadMultiXactIdRange, _ReadNextMultiXactId, _RecordFreeIndexPage, _RecoveryInProgress, _RegisterBackgroundWorker, _RegisterDynamicBackgroundWorker, _RegisterSnapshot, _RegisterSubXactCallback, _RegisterXactCallback, _RelationGetIndexList, _RelationGetIndexScan, _RelationGetNumberOfBlocksInFork, _RelationIsVisible, _ReleaseAllPlanCacheRefsInOwner, _ReleaseBuffer, _ReleaseCachedPlan, _ReleaseCatCacheList, _ReleaseCurrentSubTransaction, _ReleaseExternalFD, _ReleaseSysCache, _RelidByRelfilenumber, _RelnameGetRelid, _RequestAddinShmemSpace, _RequestNamedLWLockTranche, _ResetLatch, _ResourceOwnerCreate, _ResourceOwnerDelete, _ResourceOwnerEnlarge, _ResourceOwnerForget, _ResourceOwnerRemember, _RestoreBlockImage, _RestrictSearchPath, _RmgrNotFound, _RollbackAndReleaseCurrentSubTransaction, _ScanKeyInit, _ScanKeywordLookup, _SearchPathMatchesCurrentEnvironment, _SearchSysCache1, _SearchSysCacheAttName, _SearchSysCacheList, _SetConfigOption, _SetTuplestoreDestReceiverParams, _SetUserIdAndSecContext, _ShmemInitHash, _ShmemInitStruct, _SignalHandlerForConfigReload, _SignalHandlerForShutdownRequest, _SplitIdentifierString, _StartTransactionCommand, _SysCacheGetAttrNotNull, _SystemFuncName, _TimestampDifferenceMilliseconds, _TransactionIdDidCommit, _TransactionIdIsCurrentTransactionId, _TransactionIdIsInProgress, _TransactionIdPrecedes, _TransferExpandedObject, _TupleDescGetAttInMetadata, _TupleDescInitEntry, _TupleDescInitEntryCollation, _UnlockPage, _UnlockRelationForExtension, _UnlockReleaseBuffer, _UnpinPortal, _UnregisterSnapshot, _UpdateActiveSnapshotCommandId, _WaitEventExtensionNew, _WaitForBackgroundWorkerShutdown, _WaitForBackgroundWorkerStartup, _WaitForParallelWorkersToAttach, _WaitForParallelWorkersToFinish, _WaitLatch, _WaitLatchOrSocket, _WalUsageAccumDiff, _XLogBeginInsert, _XLogFindNextRecord, _XLogFlush, _XLogInsert, _XLogReadRecord, _XLogReaderAllocate, _XLogReaderFree, _XLogRecGetBlockRefInfo, _XLogRecGetBlockTagExtended, _XLogRecStoreStats, _XLogRegisterData, _XidInMVCCSnapshot, _accumArrayResult, _acos, _addRangeTableEntryForENR, _addRangeTableEntryForSubquery, _appendBinaryStringInfo, _appendStringInfo, _appendStringInfoChar, _appendStringInfoSpaces, _appendStringInfoString, _appendStringInfoStringQuoted, _arraycontjoinsel, _arraycontsel, _asin, _atexit, _atoi, _bitcmp, _biteq, _bitge, _bitgt, _bitle, _bitlt, _boolin, _boolout, _bpcharcmp, _bpchareq, _bpcharge, _bpchargt, _bpcharle, _bpcharlt, _bsearch, _btboolcmp, _btcharcmp, _btfloat4cmp, _btfloat8cmp, _btint2cmp, _btint4cmp, _btint8cmp, _btnamecmp, _btoidcmp, _bttextcmp, _byteacmp, _byteaeq, _byteage, _byteagt, _byteale, _bytealt, _calloc, _checkExprHasSubLink, _clearerr, _close, _connect, _copyObjectImpl, _cos, _datumCopy, _datumIsEqual, _datumTransfer, _defGetBoolean, _defGetString, _deflate, _deflateEnd, _die, _enlargeStringInfo, _equal, _errcode, _errdetail, _errfinish, _errhidestmt, _errhint, _errmsg, _errposition, _errstart, _exprCollation, _exprIsLengthCoercion, _exprLocation, _exprType, _exprTypmod, _fcntl, _ferror, _fflush, _fileno, _fopen, _fread, _free, _fscanf, _fstat, _ftruncate, _fwrite, _genericcostestimate, _getClosestMatch, _getExtensionOfObject, _getTypeInputInfo, _getTypeOutputInfo, _getc, _getegid, _getenv, _geterrposition, _geteuid, _getgid, _getinternalerrposition, _getmissingattr, _getpid, _getsockname, _getsockopt, _gettimeofday, _getuid, _ginPostingListDecode, _gistcheckpage, _gmtime, _htonl, _htons, _inflate, _inflateEnd, _initArrayResult, _initClosestMatch, _initStringInfo, _internalerrposition, _internalerrquery, _ioctl, _isalnum, _isxdigit, _lappend, _log, _lowerstr, _main, _makeAlias, _makeArrayResult, _makeBoolean, _makeColumnDef, _makeConst, _makeDefElem, _makeFuncCall, _makeInteger, _makeObjectName, _makeParamList, _makeRangeVar, _makeRangeVarFromNameList, _makeString, _makeStringInfo, _makeTargetEntry, _makeTypeName, _makeTypeNameFromNameList, _makeVar, _malloc, _memchr, _memcmp, _memcpy, _memmove, _memset, _namein, _nanosleep, _nextval, _nocachegetattr, _nodeToString, _ntohs, _oidin, _oidout, _open, _palloc, _palloc0, _pchomp, _performMultipleDeletions, _perror, _pfree, _pnstrdup, _poll, _psprintf, _pstrdup, _pushJsonbValue, _puts, _pwrite, _qsort, _rand, _read, _readdir, _readstoplist, _realloc, _recordDependencyOn, _recordDependencyOnExpr, _recv, _repalloc, _resetStringInfo, _searchstoplist, _send, _setThrew, _setsockopt, _sin, _smgrexists, _smgrnblocks, _smgropen, _smgrpin, _smgrreadv, _smgrtruncate2, _socket, _srand, _sscanf, _stat, _strcat, _strchr, _strcmp, _strcpy, _strcspn, _strdup, _strerror, _strftime, _stringToNode, _stringToQualifiedNameList, _strlcpy, _strlen, _strncat, _strncmp, _strncpy, _strrchr, _strspn, _strstr, _strtod, _strtof, _strtol, _strtoul, _strtoull, _superuser, _textToQualifiedNameList, _texteq, _tidin, _tidout, _time, _tolower, _toupper, _transformDistinctClause, _transformExpr, _transformRelOptions, _transformStmt, _typeByVal, _typeLen, _typeStringToTypeName, _typeTypeCollation, _typeidType, _typenameTypeIdAndMod, _unlink, _untransformRelOptions, _updateClosestMatch, _xmlBufferCreate, _xmlBufferFree, _xmlBufferWriteCHAR, _xmlBufferWriteChar, _xmlDocGetRootElement, _xmlEncodeSpecialChars, _xmlFreeDoc, _xmlInitParser, _xmlNodeDump, _xmlReadMemory, _xmlStrdup, _xmlStrlen, _xmlXPathCastNodeToString, _xmlXPathCastToBoolean, _xmlXPathCastToNumber, _xmlXPathCompiledEval, _xmlXPathCtxtCompile, _xmlXPathFreeCompExpr, _xmlXPathFreeContext, _xmlXPathFreeObject, _xmlXPathIsNaN, _xmlXPathNewContext, _xsltApplyStylesheetUser, _xsltCleanupGlobals, _xsltFreeSecurityPrefs, _xsltFreeStylesheet, _xsltFreeTransformContext, _xsltNewSecurityPrefs, _xsltNewTransformContext, _xsltParseStylesheetDoc, _xsltSaveResultToString, _xsltSecurityForbid, _xsltSetCtxtSecurityPrefs, _xsltSetSecurityPrefs: any;
	if (Module["wasmMemory"]) {
		wasmMemory = Module["wasmMemory"];
	} else {
		const INITIAL_MEMORY = Module["INITIAL_MEMORY"] || 16777216;
		wasmMemory = new WebAssembly.Memory({
			initial: INITIAL_MEMORY / 65536,
			maximum: 32768,
		});
	}
	const __ATPRERUN__: Array<(module: PgliteModule) => any> = [];
	const __ATINIT__: Array<(module: PgliteModule) => any> = [];
	const __ATMAIN__: Array<(module: PgliteModule) => any> = [];
	const __ATPOSTRUN__: Array<(module: PgliteModule) => any> = [];
	const __RELOC_FUNCS__: Array<(module: PgliteModule) => any> = [];
	let runtimeInitialized = false;
	function preRun() {
		if (Module["preRun"]) {
			if (typeof Module["preRun"] == "function") {
				Module["preRun"] = [Module["preRun"]];
			}
			while (Module["preRun"].length) {
				addOnPreRun(Module["preRun"].shift());
			}
		}
		callRuntimeCallbacks(__ATPRERUN__);
	}
	function initRuntime() {
		runtimeInitialized = true;
		callRuntimeCallbacks(__RELOC_FUNCS__);
		if (!Module["noFSInit"] && !FS.initialized) {
			FS.init();
		}
		FS.ignorePermissions = false;
		TTY.init();
		SOCKFS.root = FS.mount(SOCKFS, {}, null);
		PIPEFS.root = FS.mount(PIPEFS, {}, null);
		callRuntimeCallbacks(__ATINIT__);
	}
	function preMain() {
		callRuntimeCallbacks(__ATMAIN__);
	}
	function postRun() {
		if (Module["postRun"]) {
			if (typeof Module["postRun"] == "function") {
				Module["postRun"] = [Module["postRun"]];
			}
			while (Module["postRun"].length) {
				addOnPostRun(Module["postRun"].shift());
			}
		}
		callRuntimeCallbacks(__ATPOSTRUN__);
	}
	function addOnPreRun(cb: any) {
		__ATPRERUN__.unshift(cb);
	}
	function addOnInit(cb: any) {
		__ATINIT__.unshift(cb);
	}
	function addOnPostRun(cb: any) {
		__ATPOSTRUN__.unshift(cb);
	}
	let runDependencies = 0;
	let dependenciesFulfilled: null | (() => void) = null;
	function getUniqueRunDependency(id: any) {
		return id;
	}
	function addRunDependency(id: any) {
		runDependencies++;
		Module["monitorRunDependencies"]?.(runDependencies);
	}
	function removeRunDependency(id: any) {
		runDependencies--;
		Module["monitorRunDependencies"]?.(runDependencies);
		if (runDependencies == 0) {
			if (dependenciesFulfilled) {
				const callback = dependenciesFulfilled;
				dependenciesFulfilled = null;
				callback();
			}
		}
	}
	function abort(what: any) {
		Module["onAbort"]?.(what);
		what = "Aborted(" + what + ")";
		err(what);
		ABORT = true;
		what += ". Build with -sASSERTIONS for more info.";
		const e = new WebAssembly.RuntimeError(what);
		readyPromiseReject(e);
		throw e;
	}

	// 3) Wasm binary loading and instantiation
	let wasmBinaryFile: string | undefined;
	function getBinarySync(file: any) {
		if (file == wasmBinaryFile && wasmBinary) {
			return new Uint8Array(wasmBinary);
		}
		if (readBinary) {
			return readBinary(file);
		}
		throw "both async and sync fetching of the wasm failed";
	}
	async function getWasmBinary(binaryFile: any) {
		if (!wasmBinary) {
			try {
				const response = await readAsync(binaryFile);
				return new Uint8Array(response);
			} catch {}
		}
		return getBinarySync(binaryFile);
	}
	async function instantiateArrayBuffer(binaryFile: any, imports: any) {
		try {
			const binary = await getWasmBinary(binaryFile);
			const instance = await WebAssembly.instantiate(binary, imports);
			return instance;
		} catch (reason) {
			err(`failed to asynchronously prepare wasm: ${reason}`);
			abort(reason);
		}
	}
	async function instantiateAsync(binary: any, binaryFile: any, imports: any) {
		if (
			!binary &&
			typeof WebAssembly.instantiateStreaming == "function" &&
			!isDataURI(binaryFile) &&
			!ENVIRONMENT_IS_NODE &&
			typeof fetch == "function"
		) {
			try {
				const response = fetch(binaryFile, { credentials: "same-origin" });
				const instantiationResult = await WebAssembly.instantiateStreaming(
					response,
					imports,
				);
				return instantiationResult;
			} catch (reason) {
				err(`wasm streaming compile failed: ${reason}`);
				err("falling back to ArrayBuffer instantiation");
			}
		}
		return instantiateArrayBuffer(binaryFile, imports);
	}
	function getWasmImports() {
		return {
			env: wasmImports,
			wasi_snapshot_preview1: wasmImports,
			"GOT.mem": new Proxy(wasmImports, GOTHandler),
			"GOT.func": new Proxy(wasmImports, GOTHandler),
		};
	}
	async function createWasm() {
		function receiveInstance(instance: any, module: any) {
			wasmExports = instance.exports;
			wasmExports = relocateExports(wasmExports, 1024);
			const metadata = getDylinkMetadata(module);
			if (metadata.neededDynlibs) {
				dynamicLibraries = metadata.neededDynlibs.concat(dynamicLibraries);
			}
			mergeLibSymbols(wasmExports, "main");
			LDSO.init();
			loadDylibs();
			addOnInit(wasmExports["__wasm_call_ctors"]);
			__RELOC_FUNCS__.push(wasmExports["__wasm_apply_data_relocs"]);
			removeRunDependency("wasm-instantiate");
			return wasmExports;
		}
		addRunDependency("wasm-instantiate");
		const info = getWasmImports();
		if (Module["instantiateWasm"]) {
			try {
				return Module["instantiateWasm"](info, receiveInstance);
			} catch (e) {
				err(`Module["instantiateWasm"] callback failed with error: ${e}`);
				readyPromiseReject(e);
			}
		}
		wasmBinaryFile ??= findWasmBinary();
		try {
			const result = await instantiateAsync(wasmBinary, wasmBinaryFile, info);
			if (!result) {
				throw new Error("WASM instantiation returned no result");
			}
			receiveInstance(result["instance"], result["module"]);
			return result;
		} catch (e) {
			readyPromiseReject(e);
			return;
		}
	}
	const ASM_CONSTS = {
		2539960: ($0: any) => {
			Module["is_worker"] =
				typeof WorkerGlobalScope !== "undefined" &&
				self instanceof WorkerGlobalScope;
			Module["FD_BUFFER_MAX"] = $0;
			Module["emscripten_copy_to"] = console.warn;
		},
		2540132: () => {
			Module["postMessage"] = function custom_postMessage(event: any) {
				console.log("# pg_main_emsdk.c:544: onCustomMessage:", event);
			};
		},
		2540261: () => {
			if (Module["is_worker"]) {
				function onCustomMessage(event: any) {
					console.log("onCustomMessage:", event);
				}
				Module["onCustomMessage"] = onCustomMessage;
			} else {
				Module["postMessage"] = function custom_postMessage(event: any) {
					switch (event.type) {
						case "raw": {
							break;
						}
						case "stdin": {
							stringToUTF8(event.data, 1, Module["FD_BUFFER_MAX"]);
							break;
						}
						case "rcon": {
							break;
						}
						default:
							console.warn("custom_postMessage?", event);
					}
				};
			}
		},
	};
	const GOT = {};
	let currentModuleWeakSymbols: Set<string> = new Set();
		const GOTHandler = {
			get(obj: any, symName: string) {
				let rtn = GOT[symName];
			if (!rtn) {
				rtn = GOT[symName] = new WebAssembly.Global({
					value: "i32",
					mutable: true,
				});
			}
			if (!currentModuleWeakSymbols.has(symName)) {
				rtn.required = true;
			}
			return rtn;
		},
	};
	const callRuntimeCallbacks = (callbacks: any) => {
		while (callbacks.length > 0) {
			callbacks.shift()(Module);
		}
	};
		type DylinkMetadata = {
			neededDynlibs: string[];
			tlsExports: Set<string>;
			weakImports: Set<string>;
			memorySize: number;
			memoryAlign: number;
			tableSize: number;
			tableAlign: number;
		};
		const getDylinkMetadata = (binary: any): DylinkMetadata => {
		let offset = 0;
		let end = 0;
		function getU8() {
			return binary[offset++];
		}
		function getLEB() {
			let ret = 0;
			let mul = 1;
			while (1) {
				const byte = binary[offset++];
				ret += (byte & 127) * mul;
				mul *= 128;
				if (!(byte & 128)) break;
			}
			return ret;
		}
		function getString() {
			const len = getLEB();
			offset += len;
			return UTF8ArrayToString(binary, offset - len, len);
		}
		function failIf(condition: any, message: any) {
			if (condition) throw new Error(message);
		}
		let name = "dylink.0";
		if (binary instanceof WebAssembly.Module) {
			let dylinkSection = WebAssembly.Module["customSections"](binary, name);
			if (dylinkSection.length === 0) {
				name = "dylink";
				dylinkSection = WebAssembly.Module["customSections"](binary, name);
			}
			failIf(dylinkSection.length === 0, "need dylink section");
			binary = new Uint8Array(dylinkSection[0]);
			end = binary.length;
		} else {
			const int32View = new Uint32Array(
				new Uint8Array(binary.subarray(0, 24)).buffer,
			);
			const magicNumberFound = int32View[0] == 1836278016;
			failIf(!magicNumberFound, "need to see wasm magic number");
			failIf(binary[8] !== 0, "need the dylink section to be first");
			offset = 9;
			const section_size = getLEB();
			end = offset + section_size;
			name = getString();
		}
			const customSection: DylinkMetadata = {
				neededDynlibs: [],
				tlsExports: new Set<string>(),
				weakImports: new Set<string>(),
				memorySize: 0,
				memoryAlign: 0,
				tableSize: 0,
				tableAlign: 0,
			};
		if (name == "dylink") {
			customSection.memorySize = getLEB();
			customSection.memoryAlign = getLEB();
			customSection.tableSize = getLEB();
			customSection.tableAlign = getLEB();
			const neededDynlibsCount = getLEB();
			for (let i = 0; i < neededDynlibsCount; ++i) {
				const libname = getString();
				customSection.neededDynlibs.push(libname);
			}
		} else {
			failIf(name !== "dylink.0", "unsupported dylink section name");
			const WASM_DYLINK_MEM_INFO = 1;
			const WASM_DYLINK_NEEDED = 2;
			const WASM_DYLINK_EXPORT_INFO = 3;
			const WASM_DYLINK_IMPORT_INFO = 4;
			const WASM_SYMBOL_TLS = 256;
			const WASM_SYMBOL_BINDING_MASK = 3;
			const WASM_SYMBOL_BINDING_WEAK = 1;
			while (offset < end) {
				const subsectionType = getU8();
				const subsectionSize = getLEB();
				if (subsectionType === WASM_DYLINK_MEM_INFO) {
					customSection.memorySize = getLEB();
					customSection.memoryAlign = getLEB();
					customSection.tableSize = getLEB();
					customSection.tableAlign = getLEB();
					} else if (subsectionType === WASM_DYLINK_NEEDED) {
						const neededDynlibsCount = getLEB();
						let libname: string;
						for (let i = 0; i < neededDynlibsCount; ++i) {
							libname = getString();
							customSection.neededDynlibs.push(libname);
					}
				} else if (subsectionType === WASM_DYLINK_EXPORT_INFO) {
					let count = getLEB();
					while (count--) {
						const symname = getString();
						const flags = getLEB();
						if (flags & WASM_SYMBOL_TLS) {
							customSection.tlsExports.add(symname);
						}
					}
				} else if (subsectionType === WASM_DYLINK_IMPORT_INFO) {
					let count = getLEB();
					while (count--) {
						const modname = getString();
						const symname = getString();
						const flags = getLEB();
						if (
							(flags & WASM_SYMBOL_BINDING_MASK) ==
							WASM_SYMBOL_BINDING_WEAK
						) {
							customSection.weakImports.add(symname);
						}
					}
				} else {
					offset += subsectionSize;
				}
			}
		}
		return customSection;
	};
	const newDSO = (name: any, handle: any, syms: any) => {
		const dso = {
			refcount: Infinity,
			name,
			exports: syms,
			global: true,
		};
		LDSO.loadedLibsByName[name] = dso;
		if (handle != undefined) {
			LDSO.loadedLibsByHandle[handle] = dso;
		}
		return dso;
	};
	const LDSO = {
		loadedLibsByName: {},
		loadedLibsByHandle: {},
		init() {
			newDSO("__main__", 0, wasmImports);
		},
	};
	let ___heap_base = 2768080;
	const alignMemory = (size: any, alignment: any) =>
		Math.ceil(size / alignment) * alignment;
	const getMemory = (size: any) => {
		if (runtimeInitialized) {
			return _calloc(size, 1);
		}
		const ret = ___heap_base;
		const end = ret + alignMemory(size, 16);
		___heap_base = end;
		GOT["__heap_base"].value = end;
		return ret;
	};
	const isInternalSym = (symName: any) =>
		[
			"__cpp_exception",
			"__c_longjmp",
			"__wasm_apply_data_relocs",
			"__dso_handle",
			"__tls_size",
			"__tls_align",
			"__set_stack_limits",
			"_emscripten_tls_init",
			"__wasm_init_tls",
			"__wasm_call_ctors",
			"__start_em_asm",
			"__stop_em_asm",
			"__start_em_js",
			"__stop_em_js",
		].includes(symName) || symName.startsWith("__em_js__");
	const uleb128Encode = (n: any, target: any) => {
		if (n < 128) {
			target.push(n);
		} else {
			target.push((n % 128) | 128, n >> 7);
		}
	};
	const sigToWasmTypes = (sig: any) => {
		const typeNames = {
			i: "i32",
			j: "i64",
			f: "f32",
			d: "f64",
			e: "externref",
			p: "i32",
		};
		const type = {
			parameters: [],
			results: sig[0] == "v" ? [] : [typeNames[sig[0]]],
		};
		for (let i = 1; i < sig.length; ++i) {
			type.parameters.push(typeNames[sig[i]]);
		}
		return type;
	};
	const generateFuncType = (sig: any, target: any) => {
		const sigRet = sig.slice(0, 1);
		const sigParam = sig.slice(1);
		const typeCodes = { i: 127, p: 127, j: 126, f: 125, d: 124, e: 111 };
		target.push(96);
		uleb128Encode(sigParam.length, target);
		for (let i = 0; i < sigParam.length; ++i) {
			target.push(typeCodes[sigParam[i]]);
		}
		if (sigRet == "v") {
			target.push(0);
		} else {
			target.push(1, typeCodes[sigRet]);
		}
	};
	const convertJsFunctionToWasm = (func: any, sig: any) => {
		if (typeof WebAssembly.Function == "function") {
			return new WebAssembly.Function(sigToWasmTypes(sig), func);
		}
		const typeSectionBody = [1];
		generateFuncType(sig, typeSectionBody);
		const bytes = [0, 97, 115, 109, 1, 0, 0, 0, 1];
		uleb128Encode(typeSectionBody.length, bytes);
		bytes.push(...typeSectionBody);
		bytes.push(2, 7, 1, 1, 101, 1, 102, 0, 0, 7, 5, 1, 1, 102, 0, 0);
		const module = new WebAssembly.Module(new Uint8Array(bytes));
		const instance = new WebAssembly.Instance(module, { e: { f: func } });
		const wrappedFunc = instance.exports["f"];
		return wrappedFunc;
	};
	const wasmTableMirror: any[] = [];
	const wasmTable = new WebAssembly.Table({ initial: 5610, element: "anyfunc" });
	const getWasmTableEntry = (funcPtr: any) => {
		let func = wasmTableMirror[funcPtr];
		if (!func) {
			if (funcPtr >= wasmTableMirror.length) {
				wasmTableMirror.length = funcPtr + 1;
			}
			wasmTableMirror[funcPtr] = func = wasmTable.get(funcPtr);
		}
		return func;
	};
	const updateTableMap = (offset: any, count: any) => {
		if (functionsInTableMap) {
			for (let i = offset; i < offset + count; i++) {
				const item = getWasmTableEntry(i);
				if (item) {
					functionsInTableMap.set(item, i);
				}
			}
		}
	};
	let functionsInTableMap: WeakMap<Function, number> | undefined;
	const getFunctionAddress = (func: any) => {
		if (!functionsInTableMap) {
			functionsInTableMap = new WeakMap();
			updateTableMap(0, wasmTable.length);
		}
		return functionsInTableMap.get(func) || 0;
	};
	const freeTableIndexes: number[] = [];
	const getEmptyTableSlot = () => {
		if (freeTableIndexes.length) {
			return freeTableIndexes.pop();
		}
		try {
			wasmTable.grow(1);
		} catch (err) {
			if (!(err instanceof RangeError)) {
				throw err;
			}
			throw "Unable to grow wasm table. Set ALLOW_TABLE_GROWTH.";
		}
		return wasmTable.length - 1;
	};
	const setWasmTableEntry = (idx: any, func: any) => {
		wasmTable.set(idx, func);
		wasmTableMirror[idx] = wasmTable.get(idx);
	};
	const addFunction = (func: any, sig: any) => {
		const rtn = getFunctionAddress(func);
		if (rtn) {
			return rtn;
		}
		const ret = getEmptyTableSlot();
		try {
			setWasmTableEntry(ret, func);
		} catch (err) {
			if (!(err instanceof TypeError)) {
				throw err;
			}
			const wrapped = convertJsFunctionToWasm(func, sig);
			setWasmTableEntry(ret, wrapped);
		}
		functionsInTableMap.set(func, ret);
		return ret;
	};
	const updateGOT = (exports: any, replace: any) => {
		for (const symName in exports) {
			if (isInternalSym(symName)) {
				continue;
			}
			const value = exports[symName];
			GOT[symName] ||= new WebAssembly.Global({ value: "i32", mutable: true });
			if (replace || GOT[symName].value == 0) {
				if (typeof value == "function") {
					GOT[symName].value = addFunction(value);
				} else if (typeof value == "number") {
					GOT[symName].value = value;
				} else {
					err(`unhandled export type for '${symName}': ${typeof value}`);
				}
			}
		}
	};
		const relocateExports = (exports: any, memoryBase: any, replace?: any) => {
		const relocated = {};
		for (const e in exports) {
			let value = exports[e];
			if (typeof value == "object") {
				value = value.value;
			}
			if (typeof value == "number") {
				value += memoryBase;
			}
			relocated[e] = value;
		}
		updateGOT(relocated, replace);
		return relocated;
	};
	const isSymbolDefined = (symName: any) => {
		const existing = wasmImports[symName];
		if (!existing || existing.stub) {
			return false;
		}
		return true;
	};
	const dynCall = (sig: any, ptr: any, args: any[] = []) => {
		const rtn = getWasmTableEntry(ptr)(...args);
		return rtn;
	};
	const stackSave = () => _emscripten_stack_get_current();
	const stackRestore = (val: any) => __emscripten_stack_restore(val);
	const createInvokeFunction =
		(sig: any) =>
		(ptr: any, ...args: any) => {
			const sp = stackSave();
			try {
				return dynCall(sig, ptr, args);
			} catch (e: any) {
				stackRestore(sp);
				if (e !== e + 0) throw e;
				_setThrew(1, 0);
				if (sig[0] == "j") return 0n;
			}
		};
		const resolveGlobalSymbol = (symName: string, direct: boolean = false) => {
		let sym: any;
		if (isSymbolDefined(symName)) {
			sym = wasmImports[symName];
		} else if (symName.startsWith("invoke_")) {
			sym = wasmImports[symName] = createInvokeFunction(symName.split("_")[1]);
		}
		return { sym, name: symName };
	};
	let HEAP8: any, HEAPU8: any, HEAP16: any, HEAPU16: any, HEAP32: any, HEAPU32: any, HEAPF32: any, HEAP64: any, HEAPU64: any, HEAPF64: any;
	function updateMemoryViews() {
		const b = wasmMemory.buffer;
		Module["HEAP8"] = HEAP8 = new Int8Array(b);
		Module["HEAP16"] = HEAP16 = new Int16Array(b);
		Module["HEAPU8"] = HEAPU8 = new Uint8Array(b);
		Module["HEAPU16"] = HEAPU16 = new Uint16Array(b);
		Module["HEAP32"] = HEAP32 = new Int32Array(b);
		Module["HEAPU32"] = HEAPU32 = new Uint32Array(b);
		Module["HEAPF32"] = HEAPF32 = new Float32Array(b);
		Module["HEAPF64"] = HEAPF64 = new Float64Array(b);
		Module["HEAP64"] = HEAP64 = new BigInt64Array(b);
		Module["HEAPU64"] = HEAPU64 = new BigUint64Array(b);
	}
	function getValue(ptr: number, type: string = "i8") {
		if (type.endsWith("*")) type = "*";
		switch (type) {
			case "i1":
				return HEAP8[ptr];
			case "i8":
				return HEAP8[ptr];
			case "i16":
				return HEAP16[ptr >> 1];
			case "i32":
				return HEAP32[ptr >> 2];
			case "i64":
				return HEAP64[ptr >> 3];
			case "float":
				return HEAPF32[ptr >> 2];
			case "double":
				return HEAPF64[ptr >> 3];
			case "*":
				return HEAPU32[ptr >> 2];
			default:
				abort(`invalid type for getValue: ${type}`);
		}
	}
	updateMemoryViews();
	const UTF8ToString = (ptr: any, maxBytesToRead: any) =>
		ptr ? UTF8ArrayToString(HEAPU8, ptr, maxBytesToRead) : "";
	const loadWebAssemblyModule = (binary: any, flags: any, libName: any, localScope: any, handle: any) => {
		const metadata = getDylinkMetadata(binary);
		currentModuleWeakSymbols = metadata.weakImports;
		function loadModule() {
			const firstLoad = !handle || !HEAP8[handle + 8];
			if (firstLoad) {
				const memAlign = 2 ** metadata.memoryAlign;
				const memoryBase = metadata.memorySize
					? alignMemory(getMemory(metadata.memorySize + memAlign), memAlign)
					: 0;
				const tableBase = metadata.tableSize ? wasmTable.length : 0;
				if (handle) {
					HEAP8[handle + 8] = 1;
					HEAPU32[(handle + 12) >> 2] = memoryBase;
					HEAP32[(handle + 16) >> 2] = metadata.memorySize;
					HEAPU32[(handle + 20) >> 2] = tableBase;
					HEAP32[(handle + 24) >> 2] = metadata.tableSize;
				}
			} else {
				memoryBase = HEAPU32[(handle + 12) >> 2];
				tableBase = HEAPU32[(handle + 20) >> 2];
			}
			const tableGrowthNeeded = tableBase + metadata.tableSize - wasmTable.length;
			if (tableGrowthNeeded > 0) {
				wasmTable.grow(tableGrowthNeeded);
			}
			let moduleExports: any;
			function resolveSymbol(sym: any) {
				let resolved = resolveGlobalSymbol(sym).sym;
				if (!resolved && localScope) {
					resolved = localScope[sym];
				}
				if (!resolved) {
					resolved = moduleExports[sym];
				}
				return resolved;
			}
			const proxyHandler = {
				get(stubs, prop) {
					switch (prop) {
						case "__memory_base":
							return memoryBase;
						case "__table_base":
							return tableBase;
					}
					if (prop in wasmImports && !wasmImports[prop].stub) {
						return wasmImports[prop];
					}
					if (!(prop in stubs)) {
						let resolved: any;
						stubs[prop] = (...args) => {
							resolved ||= resolveSymbol(prop);
							return resolved(...args);
						};
					}
					return stubs[prop];
				},
			};
			const proxy = new Proxy({}, proxyHandler);
			const info = {
				"GOT.mem": new Proxy({}, GOTHandler),
				"GOT.func": new Proxy({}, GOTHandler),
				env: proxy,
				wasi_snapshot_preview1: proxy,
			};
			function postInstantiation(module: any, instance: any) {
				updateTableMap(tableBase, metadata.tableSize);
				moduleExports = relocateExports(instance.exports, memoryBase);
				if (!flags.allowUndefined) {
					reportUndefinedSymbols();
				}
				function addEmAsm(addr: any, body: any) {
					let args: string[] = [];
					let arity = 0;
					for (; arity < 16; arity++) {
						if (body.indexOf("$" + arity) != -1) {
							args.push("$" + arity);
						} else {
							break;
						}
					}
					args = args.join(",");
					const func = `(${args}) => { ${body} };`;
					ASM_CONSTS[start] = eval(func);
				}
				if ("__start_em_asm" in moduleExports) {
					let start = moduleExports["__start_em_asm"];
					const stop = moduleExports["__stop_em_asm"];
					while (start < stop) {
						const jsString = UTF8ToString(start);
						addEmAsm(start, jsString);
						start = HEAPU8.indexOf(0, start) + 1;
					}
				}
				function addEmJs(name: any, cSig: any, body: any) {
					const jsArgs: string[] = [];
					cSig = cSig.slice(1, -1);
					if (cSig != "void") {
						cSig = cSig.split(",");
						for (const i in cSig) {
							const jsArg = cSig[i].split(" ").pop();
							jsArgs.push(jsArg.replace("*", ""));
						}
					}
					const func = `(${jsArgs}) => ${body};`;
					moduleExports[name] = eval(func);
				}
				for (const name in moduleExports) {
					if (name.startsWith("__em_js__")) {
						const start = moduleExports[name];
						const jsString = UTF8ToString(start);
						const parts = jsString.split("<::>");
						addEmJs(name.replace("__em_js__", ""), parts[0], parts[1]);
						delete moduleExports[name];
					}
				}
				const applyRelocs = moduleExports["__wasm_apply_data_relocs"];
				if (applyRelocs) {
					if (runtimeInitialized) {
						applyRelocs();
					} else {
						__RELOC_FUNCS__.push(applyRelocs);
					}
				}
				const init = moduleExports["__wasm_call_ctors"];
				if (init) {
					if (runtimeInitialized) {
						init();
					} else {
						__ATINIT__.push(init);
					}
				}
				return moduleExports;
			}
			if (flags.loadAsync) {
				if (binary instanceof WebAssembly.Module) {
					const instance = new WebAssembly.Instance(binary, info);
					return Promise.resolve(postInstantiation(binary, instance));
				}
				return WebAssembly.instantiate(binary, info).then((result: any) =>
					postInstantiation(result.module, result.instance),
				);
			}
			const module =
				binary instanceof WebAssembly.Module
					? binary
					: new WebAssembly.Module(binary);
			const instance = new WebAssembly.Instance(module, info);
			return postInstantiation(module, instance);
		}
		if (flags.loadAsync) {
			return metadata.neededDynlibs
				.reduce(
					(chain: any, dynNeeded: any) =>
						chain.then(() => loadDynamicLibrary(dynNeeded, flags, localScope)),
					Promise.resolve(),
				)
				.then(loadModule);
		}
		metadata.neededDynlibs.forEach((needed: any) =>
			loadDynamicLibrary(needed, flags, localScope),
		);
		return loadModule();
	};
	const mergeLibSymbols = (exports: any, libName: any) => {
		for (const [sym, exp] of Object.entries(exports)) {
			const setImport = (target: any) => {
				if (!isSymbolDefined(target)) {
					wasmImports[target] = exp;
				}
			};
			setImport(sym);
			const main_alias = "__main_argc_argv";
			if (sym == "main") {
				setImport(main_alias);
			}
			if (sym == main_alias) {
				setImport("main");
			}
		}
	};
	const asyncLoad = async (url: any) => {
		const arrayBuffer = await readAsync(url);
		return new Uint8Array(arrayBuffer);
	};
	const preloadPlugins = Module["preloadPlugins"] || [];
	const registerWasmPlugin = () => {
		const wasmPlugin = {
			promiseChainEnd: Promise.resolve(),
			canHandle: (name: any) => !Module["noWasmDecoding"] && name.endsWith(".so"),
			handle: (byteArray: any, name: any, onload: any, onerror: any) => {
				wasmPlugin["promiseChainEnd"] = wasmPlugin["promiseChainEnd"]
					.then(() =>
						loadWebAssemblyModule(
							byteArray,
							{ loadAsync: true, nodelete: true },
							name,
							{},
						),
					)
					.then(
						(exports: any) => {
							preloadedWasm[name] = exports;
							onload(byteArray);
						},
						(error: any) => {
							err(`failed to instantiate wasm: ${name}: ${error}`);
							onerror();
						},
					);
			},
		};
		preloadPlugins.push(wasmPlugin);
	};
	const preloadedWasm = {};
	function loadDynamicLibrary(libName: any, flags = { global: true, nodelete: true }, localScope: any, handle: any, ) {
		let dso = LDSO.loadedLibsByName[libName];
		if (dso) {
			if (!flags.global) {
				if (localScope) {
					Object.assign(localScope, dso.exports);
				}
			} else if (!dso.global) {
				dso.global = true;
				mergeLibSymbols(dso.exports, libName);
			}
			if (flags.nodelete && dso.refcount !== Infinity) {
				dso.refcount = Infinity;
			}
			dso.refcount++;
			if (handle) {
				LDSO.loadedLibsByHandle[handle] = dso;
			}
			return flags.loadAsync ? Promise.resolve(true) : true;
		}
		dso = newDSO(libName, handle, "loading");
		dso.refcount = flags.nodelete ? Infinity : 1;
		dso.global = flags.global;
		function loadLibData() {
			if (handle) {
				const data = HEAPU32[(handle + 28) >> 2];
				const dataSize = HEAPU32[(handle + 32) >> 2];
				if (data && dataSize) {
					const libData = HEAP8.slice(data, data + dataSize);
					return flags.loadAsync ? Promise.resolve(libData) : libData;
				}
			}
			const libFile = locateFile(libName);
			if (flags.loadAsync) {
				return asyncLoad(libFile);
			}
			if (!readBinary) {
				throw new Error(
					`${libFile}: file not found, and synchronous loading of external files is not available`,
				);
			}
			return readBinary(libFile);
		}
		function getExports() {
			const preloaded = preloadedWasm[libName];
			if (preloaded) {
				return flags.loadAsync ? Promise.resolve(preloaded) : preloaded;
			}
			if (flags.loadAsync) {
				return loadLibData().then((libData: any) =>
					loadWebAssemblyModule(libData, flags, libName, localScope, handle),
				);
			}
			return loadWebAssemblyModule(
				loadLibData(),
				flags,
				libName,
				localScope,
				handle,
			);
		}
		function moduleLoaded(exports: any) {
			if (dso.global) {
				mergeLibSymbols(exports, libName);
			} else if (localScope) {
				Object.assign(localScope, exports);
			}
			dso.exports = exports;
		}
		if (flags.loadAsync) {
			return getExports().then((exports: any) => {
				moduleLoaded(exports);
				return true;
			});
		}
		moduleLoaded(getExports());
		return true;
	}
	const reportUndefinedSymbols = () => {
		for (const [symName, entry] of Object.entries(GOT)) {
			if (entry.value == 0) {
				const value = resolveGlobalSymbol(symName, true).sym;
				if (!value && !entry.required) {
					continue;
				}
				if (typeof value == "function") {
					entry.value = addFunction(value, value.sig);
				} else if (typeof value == "number") {
					entry.value = value;
				} else {
					throw new Error(`bad export type for '${symName}': ${typeof value}`);
				}
			}
		}
	};
	const loadDylibs = () => {
		if (!dynamicLibraries.length) {
			reportUndefinedSymbols();
			return;
		}
		addRunDependency("loadDylibs");
		dynamicLibraries
			.reduce(
				(chain: any, lib: any) =>
					chain.then(() =>
						loadDynamicLibrary(lib, {
							loadAsync: true,
							global: true,
							nodelete: true,
							allowUndefined: true,
						}),
					),
				Promise.resolve(),
			)
			.then(() => {
				reportUndefinedSymbols();
				removeRunDependency("loadDylibs");
			});
	};
	let noExitRuntime = Module["noExitRuntime"] || true;

	// 4) Low-level runtime helpers and FS/SYSCALL composition
	function setValue(ptr: number, value: any, type: string = "i8") {
		if (type.endsWith("*")) type = "*";
		switch (type) {
			case "i1":
				HEAP8[ptr] = value;
				break;
			case "i8":
				HEAP8[ptr] = value;
				break;
			case "i16":
				HEAP16[ptr >> 1] = value;
				break;
			case "i32":
				HEAP32[ptr >> 2] = value;
				break;
			case "i64":
				HEAP64[ptr >> 3] = BigInt(value);
				break;
			case "float":
				HEAPF32[ptr >> 2] = value;
				break;
			case "double":
				HEAPF64[ptr >> 3] = value;
				break;
			case "*":
				HEAPU32[ptr >> 2] = value;
				break;
			default:
				abort(`invalid type for setValue: ${type}`);
		}
	}
	const ___assert_fail = (condition: any, filename: any, line: any, func: any) =>
		abort(
			`Assertion failed: ${UTF8ToString(condition)}, at: ` +
				[
					filename ? UTF8ToString(filename) : "unknown filename",
					line,
					func ? UTF8ToString(func) : "unknown function",
				],
		);
	___assert_fail.sig = "vppip";
	const ___call_sighandler = (fp: any, sig: any) => getWasmTableEntry(fp)(sig);
	___call_sighandler.sig = "vpi";
	const ___memory_base = new WebAssembly.Global(
		{ value: "i32", mutable: false },
		1024,
	);
	Module["___memory_base"] = ___memory_base;
	const ___stack_pointer = new WebAssembly.Global(
		{ value: "i32", mutable: true },
		2768080,
	);
	Module["___stack_pointer"] = ___stack_pointer;
	let FS: any;
	let MEMFS: any;
	let IDBFS: any;
	let NODEFS: any;

	// 4-1) Host FS support + FS runtime
	const {
		randomFill,
		PATH_FS,
		TTY,
		zeroMemory,
		mmapAlloc,
		FS_modeStringToFlags,
		FS_getMode,
	} = createRuntimeHostFsSupport({
		getFS: () => FS,
		Module,
		ENVIRONMENT_IS_NODE,
		require,
		abort,
		fs,
		out,
		err,
		HEAPU8,
		alignMemory,
		emscriptenBuiltinMemalign: (...args) =>
			_emscripten_builtin_memalign(...args),
		addRunDependency,
		removeRunDependency,
		getUniqueRunDependency,
		preloadPlugins,
		asyncLoad,
	});
	const fsRuntime = createFSRuntime({
		PATH_FS,
		TTY,
		randomFill,
		FS_modeStringToFlags,
		FS_getMode,
		UTF8ArrayToString,
		lengthBytesUTF8,
		stringToUTF8Array,
		readBinary,
		intArrayFromString,
		mmapAlloc,
		HEAP8,
		Module,
		out,
		fflush: (...args) => _fflush(...args),
		ENVIRONMENT_IS_WORKER,
		fs,
	});
	FS = fsRuntime["FS"];
	MEMFS = fsRuntime["MEMFS"];
	IDBFS = fsRuntime["IDBFS"];
	NODEFS = fsRuntime["NODEFS"];

	// 4-2) Syscall/socket/runtime utility wiring
	const SYSCALLS = createSYSCALLS({
		getFS: () => FS,
		getHEAP32: () => HEAP32,
		getHEAPU32: () => HEAPU32,
		getHEAP64: () => HEAP64,
		getHEAP8: () => HEAP8,
		getHEAPU8: () => HEAPU8,
		UTF8ToString,
	});
	const syscallImplementations: any = createSyscallImplementations({
		FS,
		SYSCALLS,
		HEAP32,
		HEAP16,
		HEAPU16,
		HEAP8,
		HEAPU8,
		HEAP64,
		lengthBytesUTF8,
		stringToUTF8Array,
		Module,
		ENVIRONMENT_IS_NODE,
		require,
		TextEncoder,
		_ntohs,
		_htons,
		zeroMemory,
		assert,
		abort,
	});
	const {
		___syscall__newselect,
		___syscall_bind,
		___syscall_chdir,
		___syscall_chmod,
		___syscall_dup,
		___syscall_dup3,
		___syscall_faccessat,
		___syscall_fadvise64,
		___syscall_fallocate,
		___syscall_fcntl64,
		___syscall_fdatasync,
		___syscall_fstat64,
		___syscall_ftruncate64,
		___syscall_getcwd,
		___syscall_getdents64,
		___syscall_ioctl,
		___syscall_lstat64,
		___syscall_mkdirat,
		___syscall_newfstatat,
		___syscall_openat,
		___syscall_pipe,
		___syscall_readlinkat,
		___syscall_recvfrom,
		___syscall_renameat,
		___syscall_rmdir,
		___syscall_sendto,
		___syscall_socket,
		___syscall_stat64,
		___syscall_symlinkat,
		___syscall_truncate64,
		___syscall_unlinkat,
		bigintToI53Checked,
		stringToUTF8,
		PIPEFS,
		SOCKFS,
		inetNtop4,
		inetNtop6,
		readSockaddr,
		inetPton4,
		inetPton6,
		DNS,
		getSocketAddress,
		writeSockaddr,
	} = syscallImplementations;
	const ___table_base = new WebAssembly.Global(
		{ value: "i32", mutable: false },
		1,
	);
	Module["___table_base"] = ___table_base;
	const __abort_js = () => abort("");
	__abort_js.sig = "v";
	const ENV = {};
	const stackAlloc = (sz: any) => __emscripten_stack_alloc(sz);
	const stringToUTF8OnStack = (str: any) => {
		const size = lengthBytesUTF8(str) + 1;
		const ret = stackAlloc(size);
		stringToUTF8(str, ret, size);
		return ret;
	};
	const dlSetError = (msg: any) => {
		const sp = stackSave();
		const cmsg = stringToUTF8OnStack(msg);
		___dl_seterr(cmsg, 0);
		stackRestore(sp);
	};
	const dlopenInternal = (handle: any, jsflags: any) => {
		let filename = UTF8ToString(handle + 36);
		const flags = HEAP32[(handle + 4) >> 2];
		filename = PATH.normalize(filename);
		const global = Boolean(flags & 256);
		const localScope = global ? null : {};
		const combinedFlags = {
			global,
			nodelete: Boolean(flags & 4096),
			loadAsync: jsflags.loadAsync,
		};
		if (jsflags.loadAsync) {
			return loadDynamicLibrary(filename, combinedFlags, localScope, handle);
		}
		try {
			return loadDynamicLibrary(filename, combinedFlags, localScope, handle);
		} catch (e) {
			dlSetError(`Could not load dynamic lib: ${filename}\n${e}`);
			return 0;
		}
	};
	const __dlopen_js = (handle: any) => dlopenInternal(handle, { loadAsync: false });
	__dlopen_js.sig = "pp";
	const __dlsym_js = (handle: any, symbol: any, symbolIndex: any) => {
		symbol = UTF8ToString(symbol);
		let result: any;
		let newSymIndex: any;
		const lib = LDSO.loadedLibsByHandle[handle];
		if (!Object.hasOwn(lib.exports, symbol) || lib.exports[symbol].stub) {
			dlSetError(
				`Tried to lookup unknown symbol "${symbol}" in dynamic lib: ${lib.name}`,
			);
			return 0;
		}
		newSymIndex = Object.keys(lib.exports).indexOf(symbol);
		result = lib.exports[symbol];
		if (typeof result == "function") {
			const addr = getFunctionAddress(result);
			if (addr) {
				result = addr;
			} else {
				result = addFunction(result, result.sig);
				HEAPU32[symbolIndex >> 2] = newSymIndex;
			}
		}
		return result;
	};
	__dlsym_js.sig = "pppp";
	const __emscripten_memcpy_js = (dest: any, src: any, num: any) =>
		HEAPU8.copyWithin(dest, src, src + num);
	__emscripten_memcpy_js.sig = "vppp";
	const {
		__emscripten_runtime_keepalive_clear,
		__emscripten_system,
		__emscripten_throw_longjmp,
		__gmtime_js,
		__localtime_js,
		__mmap_js,
		__munmap_js,
		_proc_exit,
		_exit,
		_emscripten_get_now,
		__setitimer_js,
		__tzset_js,
		_emscripten_date_now,
		_clock_time_get,
		_emscripten_force_exit,
		handleException,
	} = createRuntimeUtils({
		Module,
		getABORT: () => ABORT,
		setABORT: (value: any) => {
			ABORT = value;
		},
		getEXITSTATUS: () => EXITSTATUS,
		setEXITSTATUS: (value: any) => {
			EXITSTATUS = value;
		},
		getNoExitRuntime: () => noExitRuntime,
		setNoExitRuntime: (value: any) => {
			noExitRuntime = value;
		},
		quit_,
		ENVIRONMENT_IS_NODE,
		UTF8ToString,
		require,
		bigintToI53Checked,
		HEAP32,
		HEAP64,
		HEAPU32,
		stringToUTF8,
		SYSCALLS,
		FS,
		getEmscriptenTimeout: () => __emscripten_timeout,
	});

	const readEmAsmArgsArray: Array<number | bigint> = [];
	const readEmAsmArgs = (sigPtr: any, buf: any) => {
		readEmAsmArgsArray.length = 0;
		let ch: any;
		while ((ch = HEAPU8[sigPtr++])) {
			let wide = ch != 105;
			wide &= ch != 112;
			buf += wide && buf % 8 ? 4 : 0;
			readEmAsmArgsArray.push(
				ch == 112
					? HEAPU32[buf >> 2]
					: ch == 106
						? HEAP64[buf >> 3]
						: ch == 105
							? HEAP32[buf >> 2]
							: HEAPF64[buf >> 3],
			);
			buf += wide ? 8 : 4;
		}
		return readEmAsmArgsArray;
	};
	const runEmAsmFunction = (code: any, sigPtr: any, argbuf: any) => {
		const args = readEmAsmArgs(sigPtr, argbuf);
		return ASM_CONSTS[code](...args);
	};
	const _emscripten_asm_const_int = (code: any, sigPtr: any, argbuf: any) =>
		runEmAsmFunction(code, sigPtr, argbuf);
	_emscripten_asm_const_int.sig = "ippp";
	const getHeapMax = () => 2147483648;
	const growMemory = (size: any) => {
		const b = wasmMemory.buffer;
		const pages = ((size - b.byteLength + 65535) / 65536) | 0;
		try {
			wasmMemory.grow(pages);
			updateMemoryViews();
			return 1;
		} catch (e) {}
	};
	const _emscripten_resize_heap = (requestedSize: any) => {
		const oldSize = HEAPU8.length;
		requestedSize >>>= 0;
		const maxHeapSize = getHeapMax();
		if (requestedSize > maxHeapSize) {
			return false;
		}
		for (let cutDown = 1; cutDown <= 4; cutDown *= 2) {
			let overGrownHeapSize = oldSize * (1 + 0.2 / cutDown);
			overGrownHeapSize = Math.min(
				overGrownHeapSize,
				requestedSize + 100663296,
			);
			const newSize = Math.min(
				maxHeapSize,
				alignMemory(Math.max(requestedSize, overGrownHeapSize), 65536),
			);
			const replacement = growMemory(newSize);
			if (replacement) {
				return true;
			}
		}
		return false;
	};
	_emscripten_resize_heap.sig = "ip";
	const { _environ_get, _environ_sizes_get } = createEnviron({
		thisProgram,
		ENV,
		HEAP8,
		HEAPU32,
	});
	const {
		_fd_close,
		_fd_fdstat_get,
		_fd_pread,
		_fd_pwrite,
		_fd_read,
		_fd_seek,
		_fd_sync,
		_fd_write,
	} = createFdWasiFunctions({
		SYSCALLS,
		FS,
		HEAPU32,
		HEAP64,
		HEAP16,
		HEAP8,
		bigintToI53Checked,
	});
	const { _getaddrinfo, _getnameinfo } = createAddrInfoFunctions({
		_malloc,
		HEAP32,
		HEAPU32,
		UTF8ToString,
		inetNtop6,
		inetNtop4,
		writeSockaddr,
		assert,
		_htonl,
		inetPton4,
		inetPton6,
		DNS,
		readSockaddr,
		stringToUTF8,
	});
	const stringToNewUTF8 = (str: any) => {
		const size = lengthBytesUTF8(str) + 1;
		const ret = _malloc(size);
		if (ret) stringToUTF8(str, ret, size);
		return ret;
	};
	const removeFunction = (index: any) => {
		functionsInTableMap.delete(getWasmTableEntry(index));
		setWasmTableEntry(index, null);
		freeTableIndexes.push(index);
	};
	const FS_createPath = FS.createPath;
	const FS_createDataFile = (
		parent,
		name,
		fileData,
		canRead,
		canWrite,
		canOwn,
	) => {
		FS.createDataFile(parent, name, fileData, canRead, canWrite, canOwn);
	};
	const FS_handledByPreloadPlugin = (byteArray: any, fullname: any, finish: any, onerror: any) => {
		if (typeof Browser != "undefined") Browser.init();
		let handled = false;
		preloadPlugins.forEach((plugin: any) => {
			if (handled) return;
			if (plugin["canHandle"](fullname)) {
				plugin["handle"](byteArray, fullname, finish, onerror);
				handled = true;
			}
		});
		return handled;
	};
	const FS_createPreloadedFile = (
		parent,
		name,
		url,
		canRead,
		canWrite,
		onload,
		onerror,
		dontCreateFile,
		canOwn,
		preFinish,
	) => {
		const fullname = name ? PATH_FS.resolve(PATH.join2(parent, name)) : parent;
		const dep = getUniqueRunDependency(`cp ${fullname}`);
		function processData(byteArray: any) {
			function finish(byteArray: any) {
				preFinish?.();
				if (!dontCreateFile) {
					FS_createDataFile(parent, name, byteArray, canRead, canWrite, canOwn);
				}
				onload?.();
				removeRunDependency(dep);
			}
			if (
				FS_handledByPreloadPlugin(byteArray, fullname, finish, () => {
					onerror?.();
					removeRunDependency(dep);
				})
			) {
				return;
			}
			finish(byteArray);
		}
		addRunDependency(dep);
		if (typeof url == "string") {
			asyncLoad(url).then(processData, onerror);
		} else {
			processData(url);
		}
	};
	const FS_unlink = (path: any) => FS.unlink(path);
	const FS_createLazyFile = FS.createLazyFile;
	const FS_createDevice = FS.createDevice;
	const setTempRet0 = (val: any) => __emscripten_tempret_set(val);
	const _setTempRet0 = setTempRet0;
	Module["_setTempRet0"] = _setTempRet0;
	const getTempRet0 = (val: any) => __emscripten_tempret_get();
	const _getTempRet0 = getTempRet0;
	Module["_getTempRet0"] = _getTempRet0;

	// 5) FS side effects and import table assembly
	registerWasmPlugin();
	FS.createPreloadedFile = FS_createPreloadedFile;
	FS.staticInit();
	Module["FS_createPath"] = FS.createPath;
	Module["FS_createDataFile"] = FS.createDataFile;
	Module["FS_createPreloadedFile"] = FS.createPreloadedFile;
	Module["FS_unlink"] = FS.unlink;
	Module["FS_createLazyFile"] = FS.createLazyFile;
	Module["FS_createDevice"] = FS.createDevice;
	MEMFS.doesNotExistError = new FS.ErrnoError(44);
	MEMFS.doesNotExistError.stack = "<generic error, no stack>";
	if (ENVIRONMENT_IS_NODE) {
		NODEFS.staticInit();
	}
	const {
		invoke_iii,
		invoke_viiii,
		invoke_vi,
		invoke_v,
		invoke_j,
		invoke_viiiiii,
		invoke_vii,
		invoke_iiiiii,
		invoke_i,
		invoke_ii,
		invoke_viii,
		invoke_vji,
		invoke_iiii,
		invoke_iiiiiiii,
		invoke_iiiii,
		invoke_viiiiiiiii,
		invoke_viiiii,
		invoke_jii,
		invoke_ji,
		invoke_jiiiiiiiii,
		invoke_jiiiiii,
		invoke_iiiiiiiiiiiiii,
		invoke_iiiijii,
		invoke_vijiji,
		invoke_viji,
		invoke_iiji,
		invoke_iiiiiiiii,
		invoke_iiiiiiiiiiiiiiiiii,
		invoke_iiiij,
		invoke_iiiiiii,
		invoke_vj,
		invoke_iiiiiiiiii,
		invoke_viiji,
		invoke_viiiiiiii,
		invoke_vij,
		invoke_ij,
		invoke_viiiiiii,
		invoke_viiiji,
		invoke_iiij,
		invoke_vid,
		invoke_ijiiiiii,
		invoke_viijii,
		invoke_iiiiiji,
		invoke_viijiiii,
		invoke_viij,
		invoke_jiiii,
		invoke_viiiiiiiiiiii,
		invoke_di,
		invoke_id,
		invoke_ijiiiii,
		invoke_iiiiiiiiiii,
	} = createInvokeWrappers({
		stackSave,
		stackRestore,
		getWasmTableEntry,
		_setThrew,
	});
	const wasmImports = {
		__assert_fail: ___assert_fail,
		__call_sighandler: ___call_sighandler,
		__heap_base: ___heap_base,
		__indirect_function_table: wasmTable,
		__memory_base: ___memory_base,
		__stack_pointer: ___stack_pointer,
		__syscall__newselect: ___syscall__newselect,
		__syscall_bind: ___syscall_bind,
		__syscall_chdir: ___syscall_chdir,
		__syscall_chmod: ___syscall_chmod,
		__syscall_dup: ___syscall_dup,
		__syscall_dup3: ___syscall_dup3,
		__syscall_faccessat: ___syscall_faccessat,
		__syscall_fadvise64: ___syscall_fadvise64,
		__syscall_fallocate: ___syscall_fallocate,
		__syscall_fcntl64: ___syscall_fcntl64,
		__syscall_fdatasync: ___syscall_fdatasync,
		__syscall_fstat64: ___syscall_fstat64,
		__syscall_ftruncate64: ___syscall_ftruncate64,
		__syscall_getcwd: ___syscall_getcwd,
		__syscall_getdents64: ___syscall_getdents64,
		__syscall_ioctl: ___syscall_ioctl,
		__syscall_lstat64: ___syscall_lstat64,
		__syscall_mkdirat: ___syscall_mkdirat,
		__syscall_newfstatat: ___syscall_newfstatat,
		__syscall_openat: ___syscall_openat,
		__syscall_pipe: ___syscall_pipe,
		__syscall_readlinkat: ___syscall_readlinkat,
		__syscall_recvfrom: ___syscall_recvfrom,
		__syscall_renameat: ___syscall_renameat,
		__syscall_rmdir: ___syscall_rmdir,
		__syscall_sendto: ___syscall_sendto,
		__syscall_socket: ___syscall_socket,
		__syscall_stat64: ___syscall_stat64,
		__syscall_symlinkat: ___syscall_symlinkat,
		__syscall_truncate64: ___syscall_truncate64,
		__syscall_unlinkat: ___syscall_unlinkat,
		__table_base: ___table_base,
		_abort_js: __abort_js,
		_dlopen_js: __dlopen_js,
		_dlsym_js: __dlsym_js,
		_emscripten_memcpy_js: __emscripten_memcpy_js,
		_emscripten_runtime_keepalive_clear: __emscripten_runtime_keepalive_clear,
		_emscripten_system: __emscripten_system,
		_emscripten_throw_longjmp: __emscripten_throw_longjmp,
		_gmtime_js: __gmtime_js,
		_localtime_js: __localtime_js,
		_mmap_js: __mmap_js,
		_munmap_js: __munmap_js,
		_setitimer_js: __setitimer_js,
		_tzset_js: __tzset_js,
		clock_time_get: _clock_time_get,
		emscripten_asm_const_int: _emscripten_asm_const_int,
		emscripten_date_now: _emscripten_date_now,
		emscripten_force_exit: _emscripten_force_exit,
		emscripten_get_now: _emscripten_get_now,
		emscripten_resize_heap: _emscripten_resize_heap,
		environ_get: _environ_get,
		environ_sizes_get: _environ_sizes_get,
		exit: _exit,
		fd_close: _fd_close,
		fd_fdstat_get: _fd_fdstat_get,
		fd_pread: _fd_pread,
		fd_pwrite: _fd_pwrite,
		fd_read: _fd_read,
		fd_seek: _fd_seek,
		fd_sync: _fd_sync,
		fd_write: _fd_write,
		getTempRet0: _getTempRet0,
		getaddrinfo: _getaddrinfo,
		getnameinfo: _getnameinfo,
		invoke_di,
		invoke_i,
		invoke_id,
		invoke_ii,
		invoke_iii,
		invoke_iiii,
		invoke_iiiii,
		invoke_iiiiii,
		invoke_iiiiiii,
		invoke_iiiiiiii,
		invoke_iiiiiiiii,
		invoke_iiiiiiiiii,
		invoke_iiiiiiiiiii,
		invoke_iiiiiiiiiiiiii,
		invoke_iiiiiiiiiiiiiiiiii,
		invoke_iiiiiji,
		invoke_iiiij,
		invoke_iiiijii,
		invoke_iiij,
		invoke_iiji,
		invoke_ij,
		invoke_ijiiiii,
		invoke_ijiiiiii,
		invoke_j,
		invoke_ji,
		invoke_jii,
		invoke_jiiii,
		invoke_jiiiiii,
		invoke_jiiiiiiiii,
		invoke_v,
		invoke_vi,
		invoke_vid,
		invoke_vii,
		invoke_viii,
		invoke_viiii,
		invoke_viiiii,
		invoke_viiiiii,
		invoke_viiiiiii,
		invoke_viiiiiiii,
		invoke_viiiiiiiii,
		invoke_viiiiiiiiiiii,
		invoke_viiiji,
		invoke_viij,
		invoke_viiji,
		invoke_viijii,
		invoke_viijiiii,
		invoke_vij,
		invoke_viji,
		invoke_vijiji,
		invoke_vj,
		invoke_vji,
		memory: wasmMemory,
		proc_exit: _proc_exit,
		setTempRet0: _setTempRet0,
	};
	// 6) Wasm export binding surface
	let wasmExports: Record<string, any>;
	createWasm();
	_fopen = (Module["_fopen"] = (a0: any, a1: any) =>
		(_fopen = Module["_fopen"] = wasmExports["fopen"])(a0, a1));
	_fflush = (Module["_fflush"] = (a0: any) =>
		(_fflush = Module["_fflush"] = wasmExports["fflush"])(a0));
	let ___errno_location = (Module["___errno_location"] = () =>
		(___errno_location = Module["___errno_location"] =
			wasmExports["__errno_location"])());
	_ProcessInterrupts = (Module["_ProcessInterrupts"] = () =>
		(_ProcessInterrupts = Module["_ProcessInterrupts"] =
			wasmExports["ProcessInterrupts"])());
	let _errstart_cold = (Module["_errstart_cold"] = (a0: any, a1: any) =>
		(_errstart_cold = Module["_errstart_cold"] = wasmExports["errstart_cold"])(
			a0,
			a1,
		));
	_errcode = (Module["_errcode"] = (a0: any) =>
		(_errcode = Module["_errcode"] = wasmExports["errcode"])(a0));
	_errmsg = (Module["_errmsg"] = (a0: any, a1: any) =>
		(_errmsg = Module["_errmsg"] = wasmExports["errmsg"])(a0, a1));
	_errfinish = (Module["_errfinish"] = (a0: any, a1: any, a2: any) =>
		(_errfinish = Module["_errfinish"] = wasmExports["errfinish"])(a0, a1, a2));
	_puts = (Module["_puts"] = (a0: any) =>
		(_puts = Module["_puts"] = wasmExports["puts"])(a0));
	_errstart = (Module["_errstart"] = (a0: any, a1: any) =>
		(_errstart = Module["_errstart"] = wasmExports["errstart"])(a0, a1));
	let _errmsg_internal = (Module["_errmsg_internal"] = (a0: any, a1: any) =>
		(_errmsg_internal = Module["_errmsg_internal"] =
			wasmExports["errmsg_internal"])(a0, a1));
	_errdetail = (Module["_errdetail"] = (a0: any, a1: any) =>
		(_errdetail = Module["_errdetail"] = wasmExports["errdetail"])(a0, a1));
	_errhint = (Module["_errhint"] = (a0: any, a1: any) =>
		(_errhint = Module["_errhint"] = wasmExports["errhint"])(a0, a1));
	let _pg_parse_query = (Module["_pg_parse_query"] = (a0: any) =>
		(_pg_parse_query = Module["_pg_parse_query"] =
			wasmExports["pg_parse_query"])(a0));
	_gettimeofday = (Module["_gettimeofday"] = (a0: any, a1: any) =>
		(_gettimeofday = Module["_gettimeofday"] = wasmExports["gettimeofday"])(
			a0,
			a1,
		));
	let _raw_parser = (Module["_raw_parser"] = (a0: any, a1: any) =>
		(_raw_parser = Module["_raw_parser"] = wasmExports["raw_parser"])(a0, a1));
	_initStringInfo = (Module["_initStringInfo"] = (a0: any) =>
		(_initStringInfo = Module["_initStringInfo"] =
			wasmExports["initStringInfo"])(a0));
	_appendStringInfoString = (Module["_appendStringInfoString"] = (a0: any, a1: any) =>
		(_appendStringInfoString = Module["_appendStringInfoString"] =
			wasmExports["appendStringInfoString"])(a0, a1));
	_appendStringInfo = (Module["_appendStringInfo"] = (a0: any, a1: any, a2: any) =>
		(_appendStringInfo = Module["_appendStringInfo"] =
			wasmExports["appendStringInfo"])(a0, a1, a2));
	let _errdetail_internal = (Module["_errdetail_internal"] = (a0: any, a1: any) =>
		(_errdetail_internal = Module["_errdetail_internal"] =
			wasmExports["errdetail_internal"])(a0, a1));
	_pfree = (Module["_pfree"] = (a0: any) =>
		(_pfree = Module["_pfree"] = wasmExports["pfree"])(a0));
	let _list_make1_impl = (Module["_list_make1_impl"] = (a0: any, a1: any) =>
		(_list_make1_impl = Module["_list_make1_impl"] =
			wasmExports["list_make1_impl"])(a0, a1));
	_QueryRewrite = (Module["_QueryRewrite"] = (a0: any) =>
		(_QueryRewrite = Module["_QueryRewrite"] = wasmExports["QueryRewrite"])(
			a0,
		));
	let _pg_plan_query = (Module["_pg_plan_query"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_plan_query = Module["_pg_plan_query"] = wasmExports["pg_plan_query"])(
			a0,
			a1,
			a2,
			a3,
		));
	_palloc0 = (Module["_palloc0"] = (a0: any) =>
		(_palloc0 = Module["_palloc0"] = wasmExports["palloc0"])(a0));
	_lappend = (Module["_lappend"] = (a0: any, a1: any) =>
		(_lappend = Module["_lappend"] = wasmExports["lappend"])(a0, a1));
	_GetCurrentTimestamp = (Module["_GetCurrentTimestamp"] = () =>
		(_GetCurrentTimestamp = Module["_GetCurrentTimestamp"] =
			wasmExports["GetCurrentTimestamp"])());
	let _pg_prng_double = (Module["_pg_prng_double"] = (a0: any) =>
		(_pg_prng_double = Module["_pg_prng_double"] =
			wasmExports["pg_prng_double"])(a0));
	let _pg_snprintf = (Module["_pg_snprintf"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_snprintf = Module["_pg_snprintf"] = wasmExports["pg_snprintf"])(
			a0,
			a1,
			a2,
			a3,
		));
	_die = (Module["_die"] = (a0: any) =>
		(_die = Module["_die"] = wasmExports["die"])(a0));
	let _check_stack_depth = (Module["_check_stack_depth"] = () =>
		(_check_stack_depth = Module["_check_stack_depth"] =
			wasmExports["check_stack_depth"])());
	let _pre_format_elog_string = (Module["_pre_format_elog_string"] = (a0: any, a1: any) =>
		(_pre_format_elog_string = Module["_pre_format_elog_string"] =
			wasmExports["pre_format_elog_string"])(a0, a1));
	let _format_elog_string = (Module["_format_elog_string"] = (a0: any, a1: any) =>
		(_format_elog_string = Module["_format_elog_string"] =
			wasmExports["format_elog_string"])(a0, a1));
	_pstrdup = (Module["_pstrdup"] = (a0: any) =>
		(_pstrdup = Module["_pstrdup"] = wasmExports["pstrdup"])(a0));
	_SplitIdentifierString = (Module["_SplitIdentifierString"] = (
		a0,
		a1,
		a2,
	) =>
		(_SplitIdentifierString = Module["_SplitIdentifierString"] =
			wasmExports["SplitIdentifierString"])(a0, a1, a2));
	let _list_free = (Module["_list_free"] = (a0: any) =>
		(_list_free = Module["_list_free"] = wasmExports["list_free"])(a0));
	let _pg_strcasecmp = (Module["_pg_strcasecmp"] = (a0: any, a1: any) =>
		(_pg_strcasecmp = Module["_pg_strcasecmp"] = wasmExports["pg_strcasecmp"])(
			a0,
			a1,
		));
	let _guc_malloc = (Module["_guc_malloc"] = (a0: any, a1: any) =>
		(_guc_malloc = Module["_guc_malloc"] = wasmExports["guc_malloc"])(a0, a1));
	_SetConfigOption = (Module["_SetConfigOption"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_SetConfigOption = Module["_SetConfigOption"] =
			wasmExports["SetConfigOption"])(a0, a1, a2, a3));
	let _pg_sprintf = (Module["_pg_sprintf"] = (a0: any, a1: any, a2: any) =>
		(_pg_sprintf = Module["_pg_sprintf"] = wasmExports["pg_sprintf"])(
			a0,
			a1,
			a2,
		));
	_strcmp = (Module["_strcmp"] = (a0: any, a1: any) =>
		(_strcmp = Module["_strcmp"] = wasmExports["strcmp"])(a0, a1));
	_strdup = (Module["_strdup"] = (a0: any) =>
		(_strdup = Module["_strdup"] = wasmExports["strdup"])(a0));
	_atoi = (Module["_atoi"] = (a0: any) =>
		(_atoi = Module["_atoi"] = wasmExports["atoi"])(a0));
	_strlcpy = (Module["_strlcpy"] = (a0: any, a1: any, a2: any) =>
		(_strlcpy = Module["_strlcpy"] = wasmExports["strlcpy"])(a0, a1, a2));
	let _pgl_shutdown = (Module["_pgl_shutdown"] = () =>
		(_pgl_shutdown = Module["_pgl_shutdown"] = wasmExports["pgl_shutdown"])());
	let _pgl_closed = (Module["_pgl_closed"] = () =>
		(_pgl_closed = Module["_pgl_closed"] = wasmExports["pgl_closed"])());
	_MemoryContextReset = (Module["_MemoryContextReset"] = (a0: any) =>
		(_MemoryContextReset = Module["_MemoryContextReset"] =
			wasmExports["MemoryContextReset"])(a0));
	_resetStringInfo = (Module["_resetStringInfo"] = (a0: any) =>
		(_resetStringInfo = Module["_resetStringInfo"] =
			wasmExports["resetStringInfo"])(a0));
	_getc = (Module["_getc"] = (a0: any) =>
		(_getc = Module["_getc"] = wasmExports["getc"])(a0));
	_appendStringInfoChar = (Module["_appendStringInfoChar"] = (a0: any, a1: any) =>
		(_appendStringInfoChar = Module["_appendStringInfoChar"] =
			wasmExports["appendStringInfoChar"])(a0, a1));
	_strlen = (Module["_strlen"] = (a0: any) =>
		(_strlen = Module["_strlen"] = wasmExports["strlen"])(a0));
	_strncmp = (Module["_strncmp"] = (a0: any, a1: any, a2: any) =>
		(_strncmp = Module["_strncmp"] = wasmExports["strncmp"])(a0, a1, a2));
	let _pg_fprintf = (Module["_pg_fprintf"] = (a0: any, a1: any, a2: any) =>
		(_pg_fprintf = Module["_pg_fprintf"] = wasmExports["pg_fprintf"])(
			a0,
			a1,
			a2,
		));
	let _pgstat_report_activity = (Module["_pgstat_report_activity"] = (a0: any, a1: any) =>
		(_pgstat_report_activity = Module["_pgstat_report_activity"] =
			wasmExports["pgstat_report_activity"])(a0, a1));
	_errhidestmt = (Module["_errhidestmt"] = (a0: any) =>
		(_errhidestmt = Module["_errhidestmt"] = wasmExports["errhidestmt"])(a0));
	_GetTransactionSnapshot = (Module["_GetTransactionSnapshot"] = () =>
		(_GetTransactionSnapshot = Module["_GetTransactionSnapshot"] =
			wasmExports["GetTransactionSnapshot"])());
	_PushActiveSnapshot = (Module["_PushActiveSnapshot"] = (a0: any) =>
		(_PushActiveSnapshot = Module["_PushActiveSnapshot"] =
			wasmExports["PushActiveSnapshot"])(a0));
	_AllocSetContextCreateInternal = (Module[
		"_AllocSetContextCreateInternal"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_AllocSetContextCreateInternal = Module["_AllocSetContextCreateInternal"] =
			wasmExports["AllocSetContextCreateInternal"])(a0, a1, a2, a3, a4));
	_PopActiveSnapshot = (Module["_PopActiveSnapshot"] = () =>
		(_PopActiveSnapshot = Module["_PopActiveSnapshot"] =
			wasmExports["PopActiveSnapshot"])());
	_CreateDestReceiver = (Module["_CreateDestReceiver"] = (a0: any) =>
		(_CreateDestReceiver = Module["_CreateDestReceiver"] =
			wasmExports["CreateDestReceiver"])(a0));
	_CommitTransactionCommand = (Module["_CommitTransactionCommand"] = () =>
		(_CommitTransactionCommand = Module["_CommitTransactionCommand"] =
			wasmExports["CommitTransactionCommand"])());
	_CommandCounterIncrement = (Module["_CommandCounterIncrement"] = () =>
		(_CommandCounterIncrement = Module["_CommandCounterIncrement"] =
			wasmExports["CommandCounterIncrement"])());
	_MemoryContextDelete = (Module["_MemoryContextDelete"] = (a0: any) =>
		(_MemoryContextDelete = Module["_MemoryContextDelete"] =
			wasmExports["MemoryContextDelete"])(a0));
	_StartTransactionCommand = (Module["_StartTransactionCommand"] = () =>
		(_StartTransactionCommand = Module["_StartTransactionCommand"] =
			wasmExports["StartTransactionCommand"])());
	let ___wasm_setjmp_test = (Module["___wasm_setjmp_test"] = (a0: any, a1: any) =>
		(___wasm_setjmp_test = Module["___wasm_setjmp_test"] =
			wasmExports["__wasm_setjmp_test"])(a0, a1));
	let _pg_printf = (Module["_pg_printf"] = (a0: any, a1: any) =>
		(_pg_printf = Module["_pg_printf"] = wasmExports["pg_printf"])(a0, a1));
	let ___wasm_setjmp = (Module["___wasm_setjmp"] = (a0: any, a1: any, a2: any) =>
		(___wasm_setjmp = Module["___wasm_setjmp"] = wasmExports["__wasm_setjmp"])(
			a0,
			a1,
			a2,
		));
	_FlushErrorState = (Module["_FlushErrorState"] = () =>
		(_FlushErrorState = Module["_FlushErrorState"] =
			wasmExports["FlushErrorState"])());
	let _emscripten_longjmp = (Module["_emscripten_longjmp"] = (a0: any, a1: any) =>
		(_emscripten_longjmp = Module["_emscripten_longjmp"] =
			wasmExports["emscripten_longjmp"])(a0, a1));
	_enlargeStringInfo = (Module["_enlargeStringInfo"] = (a0: any, a1: any) =>
		(_enlargeStringInfo = Module["_enlargeStringInfo"] =
			wasmExports["enlargeStringInfo"])(a0, a1));
	_malloc = (Module["_malloc"] = (a0: any) =>
		(_malloc = Module["_malloc"] = wasmExports["malloc"])(a0));
	_realloc = (Module["_realloc"] = (a0: any, a1: any) =>
		(_realloc = Module["_realloc"] = wasmExports["realloc"])(a0, a1));
	_getenv = (Module["_getenv"] = (a0: any) =>
		(_getenv = Module["_getenv"] = wasmExports["getenv"])(a0));
	_strspn = (Module["_strspn"] = (a0: any, a1: any) =>
		(_strspn = Module["_strspn"] = wasmExports["strspn"])(a0, a1));
	_memcpy = (Module["_memcpy"] = (a0: any, a1: any, a2: any) =>
		(_memcpy = Module["_memcpy"] = wasmExports["memcpy"])(a0, a1, a2));
	_fileno = (Module["_fileno"] = (a0: any) =>
		(_fileno = Module["_fileno"] = wasmExports["fileno"])(a0));
	_strchr = (Module["_strchr"] = (a0: any, a1: any) =>
		(_strchr = Module["_strchr"] = wasmExports["strchr"])(a0, a1));
	_free = (Module["_free"] = (a0: any) =>
		(_free = Module["_free"] = wasmExports["free"])(a0));
	let _pg_vsnprintf = (Module["_pg_vsnprintf"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_vsnprintf = Module["_pg_vsnprintf"] = wasmExports["pg_vsnprintf"])(
			a0,
			a1,
			a2,
			a3,
		));
	_strcpy = (Module["_strcpy"] = (a0: any, a1: any) =>
		(_strcpy = Module["_strcpy"] = wasmExports["strcpy"])(a0, a1));
	_psprintf = (Module["_psprintf"] = (a0: any, a1: any) =>
		(_psprintf = Module["_psprintf"] = wasmExports["psprintf"])(a0, a1));
	_stat = (Module["_stat"] = (a0: any, a1: any) =>
		(_stat = Module["_stat"] = wasmExports["stat"])(a0, a1));
	_fwrite = (Module["_fwrite"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_fwrite = Module["_fwrite"] = wasmExports["fwrite"])(a0, a1, a2, a3));
	_strftime = (Module["_strftime"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_strftime = Module["_strftime"] = wasmExports["strftime"])(
			a0,
			a1,
			a2,
			a3,
		));
	_strstr = (Module["_strstr"] = (a0: any, a1: any) =>
		(_strstr = Module["_strstr"] = wasmExports["strstr"])(a0, a1));
	_atexit = (Module["_atexit"] = (a0: any) =>
		(_atexit = Module["_atexit"] = wasmExports["atexit"])(a0));
	_strtol = (Module["_strtol"] = (a0: any, a1: any, a2: any) =>
		(_strtol = Module["_strtol"] = wasmExports["strtol"])(a0, a1, a2));
	_ferror = (Module["_ferror"] = (a0: any) =>
		(_ferror = Module["_ferror"] = wasmExports["ferror"])(a0));
	let _clear_error = (Module["_clear_error"] = () =>
		(_clear_error = Module["_clear_error"] = wasmExports["clear_error"])());
	let _interactive_one = (Module["_interactive_one"] = (a0: any, a1: any) =>
		(_interactive_one = Module["_interactive_one"] =
			wasmExports["interactive_one"])(a0, a1));
	let _pq_getmsgint = (Module["_pq_getmsgint"] = (a0: any, a1: any) =>
		(_pq_getmsgint = Module["_pq_getmsgint"] = wasmExports["pq_getmsgint"])(
			a0,
			a1,
		));
	_palloc = (Module["_palloc"] = (a0: any) =>
		(_palloc = Module["_palloc"] = wasmExports["palloc"])(a0));
	_makeParamList = (Module["_makeParamList"] = (a0: any) =>
		(_makeParamList = Module["_makeParamList"] = wasmExports["makeParamList"])(
			a0,
		));
	_getTypeInputInfo = (Module["_getTypeInputInfo"] = (a0: any, a1: any, a2: any) =>
		(_getTypeInputInfo = Module["_getTypeInputInfo"] =
			wasmExports["getTypeInputInfo"])(a0, a1, a2));
	_pnstrdup = (Module["_pnstrdup"] = (a0: any, a1: any) =>
		(_pnstrdup = Module["_pnstrdup"] = wasmExports["pnstrdup"])(a0, a1));
	_MemoryContextSetParent = (Module["_MemoryContextSetParent"] = (a0: any, a1: any) =>
		(_MemoryContextSetParent = Module["_MemoryContextSetParent"] =
			wasmExports["MemoryContextSetParent"])(a0, a1));
	let _pgl_backend = (Module["_pgl_backend"] = () =>
		(_pgl_backend = Module["_pgl_backend"] = wasmExports["pgl_backend"])());
	let _pgl_initdb = (Module["_pgl_initdb"] = () =>
		(_pgl_initdb = Module["_pgl_initdb"] = wasmExports["pgl_initdb"])());
	_main = (Module["_main"] = (a0: any, a1: any) =>
		(_main = Module["_main"] = wasmExports["__main_argc_argv"])(a0, a1));
	_appendStringInfoStringQuoted = (Module["_appendStringInfoStringQuoted"] =
		(a0: any, a1: any, a2: any) =>
			(_appendStringInfoStringQuoted = Module["_appendStringInfoStringQuoted"] =
				wasmExports["appendStringInfoStringQuoted"])(a0, a1, a2));
	let _set_errcontext_domain = (Module["_set_errcontext_domain"] = (a0: any) =>
		(_set_errcontext_domain = Module["_set_errcontext_domain"] =
			wasmExports["set_errcontext_domain"])(a0));
	let _errcontext_msg = (Module["_errcontext_msg"] = (a0: any, a1: any) =>
		(_errcontext_msg = Module["_errcontext_msg"] =
			wasmExports["errcontext_msg"])(a0, a1));
	let _pg_is_ascii = (Module["_pg_is_ascii"] = (a0: any) =>
		(_pg_is_ascii = Module["_pg_is_ascii"] = wasmExports["pg_is_ascii"])(a0));
	_memchr = (Module["_memchr"] = (a0: any, a1: any, a2: any) =>
		(_memchr = Module["_memchr"] = wasmExports["memchr"])(a0, a1, a2));
	_strrchr = (Module["_strrchr"] = (a0: any, a1: any) =>
		(_strrchr = Module["_strrchr"] = wasmExports["strrchr"])(a0, a1));
	_xsltFreeStylesheet = (Module["_xsltFreeStylesheet"] = (a0: any) =>
		(_xsltFreeStylesheet = Module["_xsltFreeStylesheet"] =
			wasmExports["xsltFreeStylesheet"])(a0));
	_xsltParseStylesheetDoc = (Module["_xsltParseStylesheetDoc"] = (a0: any) =>
		(_xsltParseStylesheetDoc = Module["_xsltParseStylesheetDoc"] =
			wasmExports["xsltParseStylesheetDoc"])(a0));
	_xsltSaveResultToString = (Module["_xsltSaveResultToString"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_xsltSaveResultToString = Module["_xsltSaveResultToString"] =
			wasmExports["xsltSaveResultToString"])(a0, a1, a2, a3));
	_xsltCleanupGlobals = (Module["_xsltCleanupGlobals"] = () =>
		(_xsltCleanupGlobals = Module["_xsltCleanupGlobals"] =
			wasmExports["xsltCleanupGlobals"])());
	_xsltNewTransformContext = (Module["_xsltNewTransformContext"] = (
		a0,
		a1,
	) =>
		(_xsltNewTransformContext = Module["_xsltNewTransformContext"] =
			wasmExports["xsltNewTransformContext"])(a0, a1));
	_xsltFreeTransformContext = (Module["_xsltFreeTransformContext"] = (a0: any) =>
		(_xsltFreeTransformContext = Module["_xsltFreeTransformContext"] =
			wasmExports["xsltFreeTransformContext"])(a0));
	_xsltApplyStylesheetUser = (Module["_xsltApplyStylesheetUser"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_xsltApplyStylesheetUser = Module["_xsltApplyStylesheetUser"] =
			wasmExports["xsltApplyStylesheetUser"])(a0, a1, a2, a3, a4, a5));
	_xsltNewSecurityPrefs = (Module["_xsltNewSecurityPrefs"] = () =>
		(_xsltNewSecurityPrefs = Module["_xsltNewSecurityPrefs"] =
			wasmExports["xsltNewSecurityPrefs"])());
	_xsltFreeSecurityPrefs = (Module["_xsltFreeSecurityPrefs"] = (a0: any) =>
		(_xsltFreeSecurityPrefs = Module["_xsltFreeSecurityPrefs"] =
			wasmExports["xsltFreeSecurityPrefs"])(a0));
	_xsltSetSecurityPrefs = (Module["_xsltSetSecurityPrefs"] = (a0: any, a1: any, a2: any) =>
		(_xsltSetSecurityPrefs = Module["_xsltSetSecurityPrefs"] =
			wasmExports["xsltSetSecurityPrefs"])(a0, a1, a2));
	_xsltSetCtxtSecurityPrefs = (Module["_xsltSetCtxtSecurityPrefs"] = (
		a0,
		a1,
	) =>
		(_xsltSetCtxtSecurityPrefs = Module["_xsltSetCtxtSecurityPrefs"] =
			wasmExports["xsltSetCtxtSecurityPrefs"])(a0, a1));
	_xsltSecurityForbid = (Module["_xsltSecurityForbid"] = (a0: any, a1: any, a2: any) =>
		(_xsltSecurityForbid = Module["_xsltSecurityForbid"] =
			wasmExports["xsltSecurityForbid"])(a0, a1, a2));
	let _replace_percent_placeholders = (Module["_replace_percent_placeholders"] =
		(a0: any, a1: any, a2: any, a3: any) =>
			(_replace_percent_placeholders = Module["_replace_percent_placeholders"] =
				wasmExports["replace_percent_placeholders"])(a0, a1, a2, a3));
	_memset = (Module["_memset"] = (a0: any, a1: any, a2: any) =>
		(_memset = Module["_memset"] = wasmExports["memset"])(a0, a1, a2));
	_MemoryContextAllocZero = (Module["_MemoryContextAllocZero"] = (a0: any, a1: any) =>
		(_MemoryContextAllocZero = Module["_MemoryContextAllocZero"] =
			wasmExports["MemoryContextAllocZero"])(a0, a1));
	_MemoryContextAllocExtended = (Module["_MemoryContextAllocExtended"] = (
		a0,
		a1,
		a2,
	) =>
		(_MemoryContextAllocExtended = Module["_MemoryContextAllocExtended"] =
			wasmExports["MemoryContextAllocExtended"])(a0, a1, a2));
	let _hash_bytes = (Module["_hash_bytes"] = (a0: any, a1: any) =>
		(_hash_bytes = Module["_hash_bytes"] = wasmExports["hash_bytes"])(a0, a1));
	_memcmp = (Module["_memcmp"] = (a0: any, a1: any, a2: any) =>
		(_memcmp = Module["_memcmp"] = wasmExports["memcmp"])(a0, a1, a2));
	_repalloc = (Module["_repalloc"] = (a0: any, a1: any) =>
		(_repalloc = Module["_repalloc"] = wasmExports["repalloc"])(a0, a1));
	let _pg_qsort = (Module["_pg_qsort"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_qsort = Module["_pg_qsort"] = wasmExports["pg_qsort"])(
			a0,
			a1,
			a2,
			a3,
		));
	_OpenTransientFile = (Module["_OpenTransientFile"] = (a0: any, a1: any) =>
		(_OpenTransientFile = Module["_OpenTransientFile"] =
			wasmExports["OpenTransientFile"])(a0, a1));
	let _errcode_for_file_access = (Module["_errcode_for_file_access"] = () =>
		(_errcode_for_file_access = Module["_errcode_for_file_access"] =
			wasmExports["errcode_for_file_access"])());
	_read = (Module["_read"] = (a0: any, a1: any, a2: any) =>
		(_read = Module["_read"] = wasmExports["read"])(a0, a1, a2));
	_CloseTransientFile = (Module["_CloseTransientFile"] = (a0: any) =>
		(_CloseTransientFile = Module["_CloseTransientFile"] =
			wasmExports["CloseTransientFile"])(a0));
	_time = (Module["_time"] = (a0: any) =>
		(_time = Module["_time"] = wasmExports["time"])(a0));
	_close = (Module["_close"] = (a0: any) =>
		(_close = Module["_close"] = wasmExports["close"])(a0));
	let ___multi3 = (Module["___multi3"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(___multi3 = Module["___multi3"] = wasmExports["__multi3"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_isalnum = (Module["_isalnum"] = (a0: any) =>
		(_isalnum = Module["_isalnum"] = wasmExports["isalnum"])(a0));
	let _wait_result_to_str = (Module["_wait_result_to_str"] = (a0: any) =>
		(_wait_result_to_str = Module["_wait_result_to_str"] =
			wasmExports["wait_result_to_str"])(a0));
	let _float_to_shortest_decimal_bufn = (Module[
		"_float_to_shortest_decimal_bufn"
	] = (a0: any, a1: any) =>
		(_float_to_shortest_decimal_bufn = Module[
			"_float_to_shortest_decimal_bufn"
		] =
			wasmExports["float_to_shortest_decimal_bufn"])(a0, a1));
	let _float_to_shortest_decimal_buf = (Module[
		"_float_to_shortest_decimal_buf"
	] = (a0: any, a1: any) =>
		(_float_to_shortest_decimal_buf = Module["_float_to_shortest_decimal_buf"] =
			wasmExports["float_to_shortest_decimal_buf"])(a0, a1));
	_memmove = (Module["_memmove"] = (a0: any, a1: any, a2: any) =>
		(_memmove = Module["_memmove"] = wasmExports["memmove"])(a0, a1, a2));
	_pwrite = (Module["_pwrite"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pwrite = Module["_pwrite"] = wasmExports["pwrite"])(a0, a1, a2, a3));
	let _hash_bytes_extended = (Module["_hash_bytes_extended"] = (a0: any, a1: any, a2: any) =>
		(_hash_bytes_extended = Module["_hash_bytes_extended"] =
			wasmExports["hash_bytes_extended"])(a0, a1, a2));
	_calloc = (a0: any, a1: any) => (_calloc = wasmExports["calloc"])(a0, a1);
	_IsValidJsonNumber = (Module["_IsValidJsonNumber"] = (a0: any, a1: any) =>
		(_IsValidJsonNumber = Module["_IsValidJsonNumber"] =
			wasmExports["IsValidJsonNumber"])(a0, a1));
	_appendBinaryStringInfo = (Module["_appendBinaryStringInfo"] = (
		a0,
		a1,
		a2,
	) =>
		(_appendBinaryStringInfo = Module["_appendBinaryStringInfo"] =
			wasmExports["appendBinaryStringInfo"])(a0, a1, a2));
	_makeStringInfo = (Module["_makeStringInfo"] = () =>
		(_makeStringInfo = Module["_makeStringInfo"] =
			wasmExports["makeStringInfo"])());
	_GetDatabaseEncodingName = (Module["_GetDatabaseEncodingName"] = () =>
		(_GetDatabaseEncodingName = Module["_GetDatabaseEncodingName"] =
			wasmExports["GetDatabaseEncodingName"])());
	_ScanKeywordLookup = (Module["_ScanKeywordLookup"] = (a0: any, a1: any) =>
		(_ScanKeywordLookup = Module["_ScanKeywordLookup"] =
			wasmExports["ScanKeywordLookup"])(a0, a1));
	_strtoul = (Module["_strtoul"] = (a0: any, a1: any, a2: any) =>
		(_strtoul = Module["_strtoul"] = wasmExports["strtoul"])(a0, a1, a2));
	_sscanf = (Module["_sscanf"] = (a0: any, a1: any, a2: any) =>
		(_sscanf = Module["_sscanf"] = wasmExports["sscanf"])(a0, a1, a2));
	_strtoull = (Module["_strtoull"] = (a0: any, a1: any, a2: any) =>
		(_strtoull = Module["_strtoull"] = wasmExports["strtoull"])(a0, a1, a2));
	let _pg_prng_uint64 = (Module["_pg_prng_uint64"] = (a0: any) =>
		(_pg_prng_uint64 = Module["_pg_prng_uint64"] =
			wasmExports["pg_prng_uint64"])(a0));
	let _pg_prng_uint32 = (Module["_pg_prng_uint32"] = (a0: any) =>
		(_pg_prng_uint32 = Module["_pg_prng_uint32"] =
			wasmExports["pg_prng_uint32"])(a0));
	_log = (Module["_log"] = (a0: any) =>
		(_log = Module["_log"] = wasmExports["log"])(a0));
	_sin = (Module["_sin"] = (a0: any) =>
		(_sin = Module["_sin"] = wasmExports["sin"])(a0));
	_readdir = (Module["_readdir"] = (a0: any) =>
		(_readdir = Module["_readdir"] = wasmExports["readdir"])(a0));
	let _forkname_to_number = (Module["_forkname_to_number"] = (a0: any) =>
		(_forkname_to_number = Module["_forkname_to_number"] =
			wasmExports["forkname_to_number"])(a0));
	_unlink = (Module["_unlink"] = (a0: any) =>
		(_unlink = Module["_unlink"] = wasmExports["unlink"])(a0));
	let _pg_utf_mblen_private = (Module["_pg_utf_mblen_private"] = (a0: any) =>
		(_pg_utf_mblen_private = Module["_pg_utf_mblen_private"] =
			wasmExports["pg_utf_mblen_private"])(a0));
	_bsearch = (Module["_bsearch"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_bsearch = Module["_bsearch"] = wasmExports["bsearch"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _palloc_extended = (Module["_palloc_extended"] = (a0: any, a1: any) =>
		(_palloc_extended = Module["_palloc_extended"] =
			wasmExports["palloc_extended"])(a0, a1));
	_appendStringInfoSpaces = (Module["_appendStringInfoSpaces"] = (a0: any, a1: any) =>
		(_appendStringInfoSpaces = Module["_appendStringInfoSpaces"] =
			wasmExports["appendStringInfoSpaces"])(a0, a1));
	_geteuid = (Module["_geteuid"] = () =>
		(_geteuid = Module["_geteuid"] = wasmExports["geteuid"])());
	_fcntl = (Module["_fcntl"] = (a0: any, a1: any, a2: any) =>
		(_fcntl = Module["_fcntl"] = wasmExports["fcntl"])(a0, a1, a2));
	let _pg_popcount_optimized = (Module["_pg_popcount_optimized"] = (a0: any, a1: any) =>
		(_pg_popcount_optimized = Module["_pg_popcount_optimized"] =
			wasmExports["pg_popcount_optimized"])(a0, a1));
	let _pg_strong_random = (Module["_pg_strong_random"] = (a0: any, a1: any) =>
		(_pg_strong_random = Module["_pg_strong_random"] =
			wasmExports["pg_strong_random"])(a0, a1));
	_open = (Module["_open"] = (a0: any, a1: any, a2: any) =>
		(_open = Module["_open"] = wasmExports["open"])(a0, a1, a2));
	let _pg_usleep = (Module["_pg_usleep"] = (a0: any) =>
		(_pg_usleep = Module["_pg_usleep"] = wasmExports["pg_usleep"])(a0));
	_nanosleep = (Module["_nanosleep"] = (a0: any, a1: any) =>
		(_nanosleep = Module["_nanosleep"] = wasmExports["nanosleep"])(a0, a1));
	_getpid = (Module["_getpid"] = () =>
		(_getpid = Module["_getpid"] = wasmExports["getpid"])());
	let _qsort_arg = (Module["_qsort_arg"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_qsort_arg = Module["_qsort_arg"] = wasmExports["qsort_arg"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_strerror = (Module["_strerror"] = (a0: any) =>
		(_strerror = Module["_strerror"] = wasmExports["strerror"])(a0));
	_RelationGetNumberOfBlocksInFork = (Module[
		"_RelationGetNumberOfBlocksInFork"
	] = (a0: any, a1: any) =>
		(_RelationGetNumberOfBlocksInFork = Module[
			"_RelationGetNumberOfBlocksInFork"
		] =
			wasmExports["RelationGetNumberOfBlocksInFork"])(a0, a1));
	_ExtendBufferedRel = (Module["_ExtendBufferedRel"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_ExtendBufferedRel = Module["_ExtendBufferedRel"] =
			wasmExports["ExtendBufferedRel"])(a0, a1, a2, a3));
	_MarkBufferDirty = (Module["_MarkBufferDirty"] = (a0: any) =>
		(_MarkBufferDirty = Module["_MarkBufferDirty"] =
			wasmExports["MarkBufferDirty"])(a0));
	_XLogBeginInsert = (Module["_XLogBeginInsert"] = () =>
		(_XLogBeginInsert = Module["_XLogBeginInsert"] =
			wasmExports["XLogBeginInsert"])());
	_XLogRegisterData = (Module["_XLogRegisterData"] = (a0: any, a1: any) =>
		(_XLogRegisterData = Module["_XLogRegisterData"] =
			wasmExports["XLogRegisterData"])(a0, a1));
	_XLogInsert = (Module["_XLogInsert"] = (a0: any, a1: any) =>
		(_XLogInsert = Module["_XLogInsert"] = wasmExports["XLogInsert"])(a0, a1));
	_UnlockReleaseBuffer = (Module["_UnlockReleaseBuffer"] = (a0: any) =>
		(_UnlockReleaseBuffer = Module["_UnlockReleaseBuffer"] =
			wasmExports["UnlockReleaseBuffer"])(a0));
	let _brin_build_desc = (Module["_brin_build_desc"] = (a0: any) =>
		(_brin_build_desc = Module["_brin_build_desc"] =
			wasmExports["brin_build_desc"])(a0));
	_EnterParallelMode = (Module["_EnterParallelMode"] = () =>
		(_EnterParallelMode = Module["_EnterParallelMode"] =
			wasmExports["EnterParallelMode"])());
	_CreateParallelContext = (Module["_CreateParallelContext"] = (
		a0,
		a1,
		a2,
	) =>
		(_CreateParallelContext = Module["_CreateParallelContext"] =
			wasmExports["CreateParallelContext"])(a0, a1, a2));
	_RegisterSnapshot = (Module["_RegisterSnapshot"] = (a0: any) =>
		(_RegisterSnapshot = Module["_RegisterSnapshot"] =
			wasmExports["RegisterSnapshot"])(a0));
	let _table_parallelscan_estimate = (Module["_table_parallelscan_estimate"] = (
		a0,
		a1,
	) =>
		(_table_parallelscan_estimate = Module["_table_parallelscan_estimate"] =
			wasmExports["table_parallelscan_estimate"])(a0, a1));
	let _add_size = (Module["_add_size"] = (a0: any, a1: any) =>
		(_add_size = Module["_add_size"] = wasmExports["add_size"])(a0, a1));
	let _tuplesort_estimate_shared = (Module["_tuplesort_estimate_shared"] = (
		a0,
	) =>
		(_tuplesort_estimate_shared = Module["_tuplesort_estimate_shared"] =
			wasmExports["tuplesort_estimate_shared"])(a0));
	_InitializeParallelDSM = (Module["_InitializeParallelDSM"] = (a0: any) =>
		(_InitializeParallelDSM = Module["_InitializeParallelDSM"] =
			wasmExports["InitializeParallelDSM"])(a0));
	_UnregisterSnapshot = (Module["_UnregisterSnapshot"] = (a0: any) =>
		(_UnregisterSnapshot = Module["_UnregisterSnapshot"] =
			wasmExports["UnregisterSnapshot"])(a0));
	_DestroyParallelContext = (Module["_DestroyParallelContext"] = (a0: any) =>
		(_DestroyParallelContext = Module["_DestroyParallelContext"] =
			wasmExports["DestroyParallelContext"])(a0));
	_ExitParallelMode = (Module["_ExitParallelMode"] = () =>
		(_ExitParallelMode = Module["_ExitParallelMode"] =
			wasmExports["ExitParallelMode"])());
	let _shm_toc_allocate = (Module["_shm_toc_allocate"] = (a0: any, a1: any) =>
		(_shm_toc_allocate = Module["_shm_toc_allocate"] =
			wasmExports["shm_toc_allocate"])(a0, a1));
	_ConditionVariableInit = (Module["_ConditionVariableInit"] = (a0: any) =>
		(_ConditionVariableInit = Module["_ConditionVariableInit"] =
			wasmExports["ConditionVariableInit"])(a0));
	let _s_init_lock_sema = (Module["_s_init_lock_sema"] = (a0: any, a1: any) =>
		(_s_init_lock_sema = Module["_s_init_lock_sema"] =
			wasmExports["s_init_lock_sema"])(a0, a1));
	let _table_parallelscan_initialize = (Module[
		"_table_parallelscan_initialize"
	] = (a0: any, a1: any, a2: any) =>
		(_table_parallelscan_initialize = Module["_table_parallelscan_initialize"] =
			wasmExports["table_parallelscan_initialize"])(a0, a1, a2));
	let _tuplesort_initialize_shared = (Module["_tuplesort_initialize_shared"] = (
		a0,
		a1,
		a2,
	) =>
		(_tuplesort_initialize_shared = Module["_tuplesort_initialize_shared"] =
			wasmExports["tuplesort_initialize_shared"])(a0, a1, a2));
	let _shm_toc_insert = (Module["_shm_toc_insert"] = (a0: any, a1: any, a2: any) =>
		(_shm_toc_insert = Module["_shm_toc_insert"] =
			wasmExports["shm_toc_insert"])(a0, a1, a2));
	_LaunchParallelWorkers = (Module["_LaunchParallelWorkers"] = (a0: any) =>
		(_LaunchParallelWorkers = Module["_LaunchParallelWorkers"] =
			wasmExports["LaunchParallelWorkers"])(a0));
	_WaitForParallelWorkersToAttach = (Module[
		"_WaitForParallelWorkersToAttach"
	] = (a0: any) =>
		(_WaitForParallelWorkersToAttach = Module[
			"_WaitForParallelWorkersToAttach"
		] =
			wasmExports["WaitForParallelWorkersToAttach"])(a0));
	let _tas_sema = (Module["_tas_sema"] = (a0: any) =>
		(_tas_sema = Module["_tas_sema"] = wasmExports["tas_sema"])(a0));
	let _s_lock = (Module["_s_lock"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_s_lock = Module["_s_lock"] = wasmExports["s_lock"])(a0, a1, a2, a3));
	let _s_unlock_sema = (Module["_s_unlock_sema"] = (a0: any) =>
		(_s_unlock_sema = Module["_s_unlock_sema"] = wasmExports["s_unlock_sema"])(
			a0,
		));
	_ConditionVariableSleep = (Module["_ConditionVariableSleep"] = (a0: any, a1: any) =>
		(_ConditionVariableSleep = Module["_ConditionVariableSleep"] =
			wasmExports["ConditionVariableSleep"])(a0, a1));
	_ConditionVariableCancelSleep = (Module["_ConditionVariableCancelSleep"] =
		() =>
			(_ConditionVariableCancelSleep = Module["_ConditionVariableCancelSleep"] =
				wasmExports["ConditionVariableCancelSleep"])());
	let _tuplesort_performsort = (Module["_tuplesort_performsort"] = (a0: any) =>
		(_tuplesort_performsort = Module["_tuplesort_performsort"] =
			wasmExports["tuplesort_performsort"])(a0));
	let _tuplesort_end = (Module["_tuplesort_end"] = (a0: any) =>
		(_tuplesort_end = Module["_tuplesort_end"] = wasmExports["tuplesort_end"])(
			a0,
		));
	let _brin_deform_tuple = (Module["_brin_deform_tuple"] = (a0: any, a1: any, a2: any) =>
		(_brin_deform_tuple = Module["_brin_deform_tuple"] =
			wasmExports["brin_deform_tuple"])(a0, a1, a2));
	let _log_newpage_buffer = (Module["_log_newpage_buffer"] = (a0: any, a1: any) =>
		(_log_newpage_buffer = Module["_log_newpage_buffer"] =
			wasmExports["log_newpage_buffer"])(a0, a1));
	_LockBuffer = (Module["_LockBuffer"] = (a0: any, a1: any) =>
		(_LockBuffer = Module["_LockBuffer"] = wasmExports["LockBuffer"])(a0, a1));
	_ReleaseBuffer = (Module["_ReleaseBuffer"] = (a0: any) =>
		(_ReleaseBuffer = Module["_ReleaseBuffer"] = wasmExports["ReleaseBuffer"])(
			a0,
		));
	_IndexGetRelation = (Module["_IndexGetRelation"] = (a0: any, a1: any) =>
		(_IndexGetRelation = Module["_IndexGetRelation"] =
			wasmExports["IndexGetRelation"])(a0, a1));
	let _table_open = (Module["_table_open"] = (a0: any, a1: any) =>
		(_table_open = Module["_table_open"] = wasmExports["table_open"])(a0, a1));
	_ReadBufferExtended = (Module["_ReadBufferExtended"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_ReadBufferExtended = Module["_ReadBufferExtended"] =
			wasmExports["ReadBufferExtended"])(a0, a1, a2, a3, a4));
	let _table_close = (Module["_table_close"] = (a0: any, a1: any) =>
		(_table_close = Module["_table_close"] = wasmExports["table_close"])(
			a0,
			a1,
		));
	let _build_reloptions = (Module["_build_reloptions"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_build_reloptions = Module["_build_reloptions"] =
			wasmExports["build_reloptions"])(a0, a1, a2, a3, a4, a5));
	_RelationGetIndexScan = (Module["_RelationGetIndexScan"] = (a0: any, a1: any, a2: any) =>
		(_RelationGetIndexScan = Module["_RelationGetIndexScan"] =
			wasmExports["RelationGetIndexScan"])(a0, a1, a2));
	let _pgstat_assoc_relation = (Module["_pgstat_assoc_relation"] = (a0: any) =>
		(_pgstat_assoc_relation = Module["_pgstat_assoc_relation"] =
			wasmExports["pgstat_assoc_relation"])(a0));
	let _index_getprocinfo = (Module["_index_getprocinfo"] = (a0: any, a1: any, a2: any) =>
		(_index_getprocinfo = Module["_index_getprocinfo"] =
			wasmExports["index_getprocinfo"])(a0, a1, a2));
	let _fmgr_info_copy = (Module["_fmgr_info_copy"] = (a0: any, a1: any, a2: any) =>
		(_fmgr_info_copy = Module["_fmgr_info_copy"] =
			wasmExports["fmgr_info_copy"])(a0, a1, a2));
	_FunctionCall4Coll = (Module["_FunctionCall4Coll"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_FunctionCall4Coll = Module["_FunctionCall4Coll"] =
			wasmExports["FunctionCall4Coll"])(a0, a1, a2, a3, a4, a5));
	_FunctionCall1Coll = (Module["_FunctionCall1Coll"] = (a0: any, a1: any, a2: any) =>
		(_FunctionCall1Coll = Module["_FunctionCall1Coll"] =
			wasmExports["FunctionCall1Coll"])(a0, a1, a2));
	let _brin_free_desc = (Module["_brin_free_desc"] = (a0: any) =>
		(_brin_free_desc = Module["_brin_free_desc"] =
			wasmExports["brin_free_desc"])(a0));
	_WaitForParallelWorkersToFinish = (Module[
		"_WaitForParallelWorkersToFinish"
	] = (a0: any) =>
		(_WaitForParallelWorkersToFinish = Module[
			"_WaitForParallelWorkersToFinish"
		] =
			wasmExports["WaitForParallelWorkersToFinish"])(a0));
	_PageGetFreeSpace = (Module["_PageGetFreeSpace"] = (a0: any) =>
		(_PageGetFreeSpace = Module["_PageGetFreeSpace"] =
			wasmExports["PageGetFreeSpace"])(a0));
	_BufferGetBlockNumber = (Module["_BufferGetBlockNumber"] = (a0: any) =>
		(_BufferGetBlockNumber = Module["_BufferGetBlockNumber"] =
			wasmExports["BufferGetBlockNumber"])(a0));
	_BuildIndexInfo = (Module["_BuildIndexInfo"] = (a0: any) =>
		(_BuildIndexInfo = Module["_BuildIndexInfo"] =
			wasmExports["BuildIndexInfo"])(a0));
	_Int64GetDatum = (Module["_Int64GetDatum"] = (a0: any) =>
		(_Int64GetDatum = Module["_Int64GetDatum"] = wasmExports["Int64GetDatum"])(
			a0,
		));
	_DirectFunctionCall2Coll = (Module["_DirectFunctionCall2Coll"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_DirectFunctionCall2Coll = Module["_DirectFunctionCall2Coll"] =
			wasmExports["DirectFunctionCall2Coll"])(a0, a1, a2, a3));
	_RecoveryInProgress = (Module["_RecoveryInProgress"] = () =>
		(_RecoveryInProgress = Module["_RecoveryInProgress"] =
			wasmExports["RecoveryInProgress"])());
	_GetUserIdAndSecContext = (Module["_GetUserIdAndSecContext"] = (a0: any, a1: any) =>
		(_GetUserIdAndSecContext = Module["_GetUserIdAndSecContext"] =
			wasmExports["GetUserIdAndSecContext"])(a0, a1));
	_SetUserIdAndSecContext = (Module["_SetUserIdAndSecContext"] = (a0: any, a1: any) =>
		(_SetUserIdAndSecContext = Module["_SetUserIdAndSecContext"] =
			wasmExports["SetUserIdAndSecContext"])(a0, a1));
	_NewGUCNestLevel = (Module["_NewGUCNestLevel"] = () =>
		(_NewGUCNestLevel = Module["_NewGUCNestLevel"] =
			wasmExports["NewGUCNestLevel"])());
	_RestrictSearchPath = (Module["_RestrictSearchPath"] = () =>
		(_RestrictSearchPath = Module["_RestrictSearchPath"] =
			wasmExports["RestrictSearchPath"])());
	let _index_open = (Module["_index_open"] = (a0: any, a1: any) =>
		(_index_open = Module["_index_open"] = wasmExports["index_open"])(a0, a1));
	let _object_ownercheck = (Module["_object_ownercheck"] = (a0: any, a1: any, a2: any) =>
		(_object_ownercheck = Module["_object_ownercheck"] =
			wasmExports["object_ownercheck"])(a0, a1, a2));
	let _aclcheck_error = (Module["_aclcheck_error"] = (a0: any, a1: any, a2: any) =>
		(_aclcheck_error = Module["_aclcheck_error"] =
			wasmExports["aclcheck_error"])(a0, a1, a2));
	let _AtEOXact_GUC = (Module["_AtEOXact_GUC"] = (a0: any, a1: any) =>
		(_AtEOXact_GUC = Module["_AtEOXact_GUC"] = wasmExports["AtEOXact_GUC"])(
			a0,
			a1,
		));
	let _relation_close = (Module["_relation_close"] = (a0: any, a1: any) =>
		(_relation_close = Module["_relation_close"] =
			wasmExports["relation_close"])(a0, a1));
	_GetUserId = (Module["_GetUserId"] = () =>
		(_GetUserId = Module["_GetUserId"] = wasmExports["GetUserId"])());
	_ReadBuffer = (Module["_ReadBuffer"] = (a0: any, a1: any) =>
		(_ReadBuffer = Module["_ReadBuffer"] = wasmExports["ReadBuffer"])(a0, a1));
	let _shm_toc_lookup = (Module["_shm_toc_lookup"] = (a0: any, a1: any, a2: any) =>
		(_shm_toc_lookup = Module["_shm_toc_lookup"] =
			wasmExports["shm_toc_lookup"])(a0, a1, a2));
	let _tuplesort_attach_shared = (Module["_tuplesort_attach_shared"] = (
		a0,
		a1,
	) =>
		(_tuplesort_attach_shared = Module["_tuplesort_attach_shared"] =
			wasmExports["tuplesort_attach_shared"])(a0, a1));
	let _index_close = (Module["_index_close"] = (a0: any, a1: any) =>
		(_index_close = Module["_index_close"] = wasmExports["index_close"])(
			a0,
			a1,
		));
	let _table_beginscan_parallel = (Module["_table_beginscan_parallel"] = (
		a0,
		a1,
	) =>
		(_table_beginscan_parallel = Module["_table_beginscan_parallel"] =
			wasmExports["table_beginscan_parallel"])(a0, a1));
	_ConditionVariableSignal = (Module["_ConditionVariableSignal"] = (a0: any) =>
		(_ConditionVariableSignal = Module["_ConditionVariableSignal"] =
			wasmExports["ConditionVariableSignal"])(a0));
	_datumCopy = (Module["_datumCopy"] = (a0: any, a1: any, a2: any) =>
		(_datumCopy = Module["_datumCopy"] = wasmExports["datumCopy"])(a0, a1, a2));
	let _lookup_type_cache = (Module["_lookup_type_cache"] = (a0: any, a1: any) =>
		(_lookup_type_cache = Module["_lookup_type_cache"] =
			wasmExports["lookup_type_cache"])(a0, a1));
	let _get_fn_opclass_options = (Module["_get_fn_opclass_options"] = (a0: any) =>
		(_get_fn_opclass_options = Module["_get_fn_opclass_options"] =
			wasmExports["get_fn_opclass_options"])(a0));
	let _pg_detoast_datum = (Module["_pg_detoast_datum"] = (a0: any) =>
		(_pg_detoast_datum = Module["_pg_detoast_datum"] =
			wasmExports["pg_detoast_datum"])(a0));
	let _index_getprocid = (Module["_index_getprocid"] = (a0: any, a1: any, a2: any) =>
		(_index_getprocid = Module["_index_getprocid"] =
			wasmExports["index_getprocid"])(a0, a1, a2));
	let _init_local_reloptions = (Module["_init_local_reloptions"] = (a0: any, a1: any) =>
		(_init_local_reloptions = Module["_init_local_reloptions"] =
			wasmExports["init_local_reloptions"])(a0, a1));
	_FunctionCall2Coll = (Module["_FunctionCall2Coll"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_FunctionCall2Coll = Module["_FunctionCall2Coll"] =
			wasmExports["FunctionCall2Coll"])(a0, a1, a2, a3));
	_SysCacheGetAttrNotNull = (Module["_SysCacheGetAttrNotNull"] = (
		a0,
		a1,
		a2,
	) =>
		(_SysCacheGetAttrNotNull = Module["_SysCacheGetAttrNotNull"] =
			wasmExports["SysCacheGetAttrNotNull"])(a0, a1, a2));
	_ReleaseSysCache = (Module["_ReleaseSysCache"] = (a0: any) =>
		(_ReleaseSysCache = Module["_ReleaseSysCache"] =
			wasmExports["ReleaseSysCache"])(a0));
	let _fmgr_info_cxt = (Module["_fmgr_info_cxt"] = (a0: any, a1: any, a2: any) =>
		(_fmgr_info_cxt = Module["_fmgr_info_cxt"] = wasmExports["fmgr_info_cxt"])(
			a0,
			a1,
			a2,
		));
	_Float8GetDatum = (Module["_Float8GetDatum"] = (a0: any) =>
		(_Float8GetDatum = Module["_Float8GetDatum"] =
			wasmExports["Float8GetDatum"])(a0));
	let _numeric_sub = (Module["_numeric_sub"] = (a0: any) =>
		(_numeric_sub = Module["_numeric_sub"] = wasmExports["numeric_sub"])(a0));
	_DirectFunctionCall1Coll = (Module["_DirectFunctionCall1Coll"] = (
		a0,
		a1,
		a2,
	) =>
		(_DirectFunctionCall1Coll = Module["_DirectFunctionCall1Coll"] =
			wasmExports["DirectFunctionCall1Coll"])(a0, a1, a2));
	let _pg_detoast_datum_packed = (Module["_pg_detoast_datum_packed"] = (a0: any) =>
		(_pg_detoast_datum_packed = Module["_pg_detoast_datum_packed"] =
			wasmExports["pg_detoast_datum_packed"])(a0));
	let _add_local_int_reloption = (Module["_add_local_int_reloption"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_add_local_int_reloption = Module["_add_local_int_reloption"] =
			wasmExports["add_local_int_reloption"])(a0, a1, a2, a3, a4, a5, a6));
	_getTypeOutputInfo = (Module["_getTypeOutputInfo"] = (a0: any, a1: any, a2: any) =>
		(_getTypeOutputInfo = Module["_getTypeOutputInfo"] =
			wasmExports["getTypeOutputInfo"])(a0, a1, a2));
	let _fmgr_info = (Module["_fmgr_info"] = (a0: any, a1: any) =>
		(_fmgr_info = Module["_fmgr_info"] = wasmExports["fmgr_info"])(a0, a1));
	_OutputFunctionCall = (Module["_OutputFunctionCall"] = (a0: any, a1: any) =>
		(_OutputFunctionCall = Module["_OutputFunctionCall"] =
			wasmExports["OutputFunctionCall"])(a0, a1));
	let _cstring_to_text_with_len = (Module["_cstring_to_text_with_len"] = (
		a0,
		a1,
	) =>
		(_cstring_to_text_with_len = Module["_cstring_to_text_with_len"] =
			wasmExports["cstring_to_text_with_len"])(a0, a1));
	_accumArrayResult = (Module["_accumArrayResult"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_accumArrayResult = Module["_accumArrayResult"] =
			wasmExports["accumArrayResult"])(a0, a1, a2, a3, a4));
	_makeArrayResult = (Module["_makeArrayResult"] = (a0: any, a1: any) =>
		(_makeArrayResult = Module["_makeArrayResult"] =
			wasmExports["makeArrayResult"])(a0, a1));
	_OidOutputFunctionCall = (Module["_OidOutputFunctionCall"] = (a0: any, a1: any) =>
		(_OidOutputFunctionCall = Module["_OidOutputFunctionCall"] =
			wasmExports["OidOutputFunctionCall"])(a0, a1));
	let _cstring_to_text = (Module["_cstring_to_text"] = (a0: any) =>
		(_cstring_to_text = Module["_cstring_to_text"] =
			wasmExports["cstring_to_text"])(a0));
	_PageGetExactFreeSpace = (Module["_PageGetExactFreeSpace"] = (a0: any) =>
		(_PageGetExactFreeSpace = Module["_PageGetExactFreeSpace"] =
			wasmExports["PageGetExactFreeSpace"])(a0));
	_PageIndexTupleOverwrite = (Module["_PageIndexTupleOverwrite"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_PageIndexTupleOverwrite = Module["_PageIndexTupleOverwrite"] =
			wasmExports["PageIndexTupleOverwrite"])(a0, a1, a2, a3));
	_PageInit = (Module["_PageInit"] = (a0: any, a1: any, a2: any) =>
		(_PageInit = Module["_PageInit"] = wasmExports["PageInit"])(a0, a1, a2));
	_PageAddItemExtended = (Module["_PageAddItemExtended"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_PageAddItemExtended = Module["_PageAddItemExtended"] =
			wasmExports["PageAddItemExtended"])(a0, a1, a2, a3, a4));
	_LockRelationForExtension = (Module["_LockRelationForExtension"] = (
		a0,
		a1,
	) =>
		(_LockRelationForExtension = Module["_LockRelationForExtension"] =
			wasmExports["LockRelationForExtension"])(a0, a1));
	_UnlockRelationForExtension = (Module["_UnlockRelationForExtension"] = (
		a0,
		a1,
	) =>
		(_UnlockRelationForExtension = Module["_UnlockRelationForExtension"] =
			wasmExports["UnlockRelationForExtension"])(a0, a1));
	_smgropen = (Module["_smgropen"] = (a0: any, a1: any) =>
		(_smgropen = Module["_smgropen"] = wasmExports["smgropen"])(a0, a1));
	_smgrpin = (Module["_smgrpin"] = (a0: any) =>
		(_smgrpin = Module["_smgrpin"] = wasmExports["smgrpin"])(a0));
	_ItemPointerEquals = (Module["_ItemPointerEquals"] = (a0: any, a1: any) =>
		(_ItemPointerEquals = Module["_ItemPointerEquals"] =
			wasmExports["ItemPointerEquals"])(a0, a1));
	let _detoast_external_attr = (Module["_detoast_external_attr"] = (a0: any) =>
		(_detoast_external_attr = Module["_detoast_external_attr"] =
			wasmExports["detoast_external_attr"])(a0));
	_CreateTemplateTupleDesc = (Module["_CreateTemplateTupleDesc"] = (a0: any) =>
		(_CreateTemplateTupleDesc = Module["_CreateTemplateTupleDesc"] =
			wasmExports["CreateTemplateTupleDesc"])(a0));
	_TupleDescInitEntry = (Module["_TupleDescInitEntry"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_TupleDescInitEntry = Module["_TupleDescInitEntry"] =
			wasmExports["TupleDescInitEntry"])(a0, a1, a2, a3, a4, a5));
	_SearchSysCache1 = (Module["_SearchSysCache1"] = (a0: any, a1: any) =>
		(_SearchSysCache1 = Module["_SearchSysCache1"] =
			wasmExports["SearchSysCache1"])(a0, a1));
	_SearchSysCacheList = (Module["_SearchSysCacheList"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_SearchSysCacheList = Module["_SearchSysCacheList"] =
			wasmExports["SearchSysCacheList"])(a0, a1, a2, a3, a4));
	let _check_amproc_signature = (Module["_check_amproc_signature"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_check_amproc_signature = Module["_check_amproc_signature"] =
			wasmExports["check_amproc_signature"])(a0, a1, a2, a3, a4, a5));
	let _check_amoptsproc_signature = (Module["_check_amoptsproc_signature"] = (
		a0,
	) =>
		(_check_amoptsproc_signature = Module["_check_amoptsproc_signature"] =
			wasmExports["check_amoptsproc_signature"])(a0));
	let _format_procedure = (Module["_format_procedure"] = (a0: any) =>
		(_format_procedure = Module["_format_procedure"] =
			wasmExports["format_procedure"])(a0));
	let _format_operator = (Module["_format_operator"] = (a0: any) =>
		(_format_operator = Module["_format_operator"] =
			wasmExports["format_operator"])(a0));
	let _check_amop_signature = (Module["_check_amop_signature"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_check_amop_signature = Module["_check_amop_signature"] =
			wasmExports["check_amop_signature"])(a0, a1, a2, a3));
	let _identify_opfamily_groups = (Module["_identify_opfamily_groups"] = (
		a0,
		a1,
	) =>
		(_identify_opfamily_groups = Module["_identify_opfamily_groups"] =
			wasmExports["identify_opfamily_groups"])(a0, a1));
	let _format_type_be = (Module["_format_type_be"] = (a0: any) =>
		(_format_type_be = Module["_format_type_be"] =
			wasmExports["format_type_be"])(a0));
	_ReleaseCatCacheList = (Module["_ReleaseCatCacheList"] = (a0: any) =>
		(_ReleaseCatCacheList = Module["_ReleaseCatCacheList"] =
			wasmExports["ReleaseCatCacheList"])(a0));
	let _format_type_with_typemod = (Module["_format_type_with_typemod"] = (
		a0,
		a1,
	) =>
		(_format_type_with_typemod = Module["_format_type_with_typemod"] =
			wasmExports["format_type_with_typemod"])(a0, a1));
	_DatumGetEOHP = (Module["_DatumGetEOHP"] = (a0: any) =>
		(_DatumGetEOHP = Module["_DatumGetEOHP"] = wasmExports["DatumGetEOHP"])(
			a0,
		));
	let _EOH_get_flat_size = (Module["_EOH_get_flat_size"] = (a0: any) =>
		(_EOH_get_flat_size = Module["_EOH_get_flat_size"] =
			wasmExports["EOH_get_flat_size"])(a0));
	let _EOH_flatten_into = (Module["_EOH_flatten_into"] = (a0: any, a1: any, a2: any) =>
		(_EOH_flatten_into = Module["_EOH_flatten_into"] =
			wasmExports["EOH_flatten_into"])(a0, a1, a2));
	_getmissingattr = (Module["_getmissingattr"] = (a0: any, a1: any, a2: any) =>
		(_getmissingattr = Module["_getmissingattr"] =
			wasmExports["getmissingattr"])(a0, a1, a2));
	let _hash_create = (Module["_hash_create"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_hash_create = Module["_hash_create"] = wasmExports["hash_create"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _hash_search = (Module["_hash_search"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_hash_search = Module["_hash_search"] = wasmExports["hash_search"])(
			a0,
			a1,
			a2,
			a3,
		));
	_nocachegetattr = (Module["_nocachegetattr"] = (a0: any, a1: any, a2: any) =>
		(_nocachegetattr = Module["_nocachegetattr"] =
			wasmExports["nocachegetattr"])(a0, a1, a2));
	let _heap_form_tuple = (Module["_heap_form_tuple"] = (a0: any, a1: any, a2: any) =>
		(_heap_form_tuple = Module["_heap_form_tuple"] =
			wasmExports["heap_form_tuple"])(a0, a1, a2));
	let _heap_modify_tuple = (Module["_heap_modify_tuple"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_heap_modify_tuple = Module["_heap_modify_tuple"] =
			wasmExports["heap_modify_tuple"])(a0, a1, a2, a3, a4));
	let _heap_deform_tuple = (Module["_heap_deform_tuple"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_heap_deform_tuple = Module["_heap_deform_tuple"] =
			wasmExports["heap_deform_tuple"])(a0, a1, a2, a3));
	let _heap_modify_tuple_by_cols = (Module["_heap_modify_tuple_by_cols"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_heap_modify_tuple_by_cols = Module["_heap_modify_tuple_by_cols"] =
			wasmExports["heap_modify_tuple_by_cols"])(a0, a1, a2, a3, a4, a5));
	let _heap_freetuple = (Module["_heap_freetuple"] = (a0: any) =>
		(_heap_freetuple = Module["_heap_freetuple"] =
			wasmExports["heap_freetuple"])(a0));
	let _index_form_tuple = (Module["_index_form_tuple"] = (a0: any, a1: any, a2: any) =>
		(_index_form_tuple = Module["_index_form_tuple"] =
			wasmExports["index_form_tuple"])(a0, a1, a2));
	let _nocache_index_getattr = (Module["_nocache_index_getattr"] = (
		a0,
		a1,
		a2,
	) =>
		(_nocache_index_getattr = Module["_nocache_index_getattr"] =
			wasmExports["nocache_index_getattr"])(a0, a1, a2));
	let _index_deform_tuple = (Module["_index_deform_tuple"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_index_deform_tuple = Module["_index_deform_tuple"] =
			wasmExports["index_deform_tuple"])(a0, a1, a2, a3));
	let _slot_getsomeattrs_int = (Module["_slot_getsomeattrs_int"] = (a0: any, a1: any) =>
		(_slot_getsomeattrs_int = Module["_slot_getsomeattrs_int"] =
			wasmExports["slot_getsomeattrs_int"])(a0, a1));
	let _pg_ltoa = (Module["_pg_ltoa"] = (a0: any, a1: any) =>
		(_pg_ltoa = Module["_pg_ltoa"] = wasmExports["pg_ltoa"])(a0, a1));
	let _relation_open = (Module["_relation_open"] = (a0: any, a1: any) =>
		(_relation_open = Module["_relation_open"] = wasmExports["relation_open"])(
			a0,
			a1,
		));
	_LockRelationOid = (Module["_LockRelationOid"] = (a0: any, a1: any) =>
		(_LockRelationOid = Module["_LockRelationOid"] =
			wasmExports["LockRelationOid"])(a0, a1));
	let _try_relation_open = (Module["_try_relation_open"] = (a0: any, a1: any) =>
		(_try_relation_open = Module["_try_relation_open"] =
			wasmExports["try_relation_open"])(a0, a1));
	let _relation_openrv = (Module["_relation_openrv"] = (a0: any, a1: any) =>
		(_relation_openrv = Module["_relation_openrv"] =
			wasmExports["relation_openrv"])(a0, a1));
	_RangeVarGetRelidExtended = (Module["_RangeVarGetRelidExtended"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_RangeVarGetRelidExtended = Module["_RangeVarGetRelidExtended"] =
			wasmExports["RangeVarGetRelidExtended"])(a0, a1, a2, a3, a4));
	let _add_reloption_kind = (Module["_add_reloption_kind"] = () =>
		(_add_reloption_kind = Module["_add_reloption_kind"] =
			wasmExports["add_reloption_kind"])());
	let _register_reloptions_validator = (Module[
		"_register_reloptions_validator"
	] = (a0: any, a1: any) =>
		(_register_reloptions_validator = Module["_register_reloptions_validator"] =
			wasmExports["register_reloptions_validator"])(a0, a1));
	let _add_int_reloption = (Module["_add_int_reloption"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_add_int_reloption = Module["_add_int_reloption"] =
			wasmExports["add_int_reloption"])(a0, a1, a2, a3, a4, a5, a6));
	_MemoryContextStrdup = (Module["_MemoryContextStrdup"] = (a0: any, a1: any) =>
		(_MemoryContextStrdup = Module["_MemoryContextStrdup"] =
			wasmExports["MemoryContextStrdup"])(a0, a1));
	_transformRelOptions = (Module["_transformRelOptions"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_transformRelOptions = Module["_transformRelOptions"] =
			wasmExports["transformRelOptions"])(a0, a1, a2, a3, a4, a5));
	let _deconstruct_array_builtin = (Module["_deconstruct_array_builtin"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_deconstruct_array_builtin = Module["_deconstruct_array_builtin"] =
			wasmExports["deconstruct_array_builtin"])(a0, a1, a2, a3, a4));
	_defGetString = (Module["_defGetString"] = (a0: any) =>
		(_defGetString = Module["_defGetString"] = wasmExports["defGetString"])(
			a0,
		));
	_defGetBoolean = (Module["_defGetBoolean"] = (a0: any) =>
		(_defGetBoolean = Module["_defGetBoolean"] = wasmExports["defGetBoolean"])(
			a0,
		));
	_untransformRelOptions = (Module["_untransformRelOptions"] = (a0: any) =>
		(_untransformRelOptions = Module["_untransformRelOptions"] =
			wasmExports["untransformRelOptions"])(a0));
	let _text_to_cstring = (Module["_text_to_cstring"] = (a0: any) =>
		(_text_to_cstring = Module["_text_to_cstring"] =
			wasmExports["text_to_cstring"])(a0));
	_makeString = (Module["_makeString"] = (a0: any) =>
		(_makeString = Module["_makeString"] = wasmExports["makeString"])(a0));
	_makeDefElem = (Module["_makeDefElem"] = (a0: any, a1: any, a2: any) =>
		(_makeDefElem = Module["_makeDefElem"] = wasmExports["makeDefElem"])(
			a0,
			a1,
			a2,
		));
	let _heap_reloptions = (Module["_heap_reloptions"] = (a0: any, a1: any, a2: any) =>
		(_heap_reloptions = Module["_heap_reloptions"] =
			wasmExports["heap_reloptions"])(a0, a1, a2));
	_MemoryContextAlloc = (Module["_MemoryContextAlloc"] = (a0: any, a1: any) =>
		(_MemoryContextAlloc = Module["_MemoryContextAlloc"] =
			wasmExports["MemoryContextAlloc"])(a0, a1));
	let _parse_bool = (Module["_parse_bool"] = (a0: any, a1: any) =>
		(_parse_bool = Module["_parse_bool"] = wasmExports["parse_bool"])(a0, a1));
	let _parse_int = (Module["_parse_int"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_parse_int = Module["_parse_int"] = wasmExports["parse_int"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _parse_real = (Module["_parse_real"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_parse_real = Module["_parse_real"] = wasmExports["parse_real"])(
			a0,
			a1,
			a2,
			a3,
		));
	_ScanKeyInit = (Module["_ScanKeyInit"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_ScanKeyInit = Module["_ScanKeyInit"] = wasmExports["ScanKeyInit"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _dsm_segment_handle = (Module["_dsm_segment_handle"] = (a0: any) =>
		(_dsm_segment_handle = Module["_dsm_segment_handle"] =
			wasmExports["dsm_segment_handle"])(a0));
	let _dsm_create = (Module["_dsm_create"] = (a0: any, a1: any) =>
		(_dsm_create = Module["_dsm_create"] = wasmExports["dsm_create"])(a0, a1));
	let _dsm_segment_address = (Module["_dsm_segment_address"] = (a0: any) =>
		(_dsm_segment_address = Module["_dsm_segment_address"] =
			wasmExports["dsm_segment_address"])(a0));
	let _dsm_attach = (Module["_dsm_attach"] = (a0: any) =>
		(_dsm_attach = Module["_dsm_attach"] = wasmExports["dsm_attach"])(a0));
	let _dsm_detach = (Module["_dsm_detach"] = (a0: any) =>
		(_dsm_detach = Module["_dsm_detach"] = wasmExports["dsm_detach"])(a0));
	_ShmemInitStruct = (Module["_ShmemInitStruct"] = (a0: any, a1: any, a2: any) =>
		(_ShmemInitStruct = Module["_ShmemInitStruct"] =
			wasmExports["ShmemInitStruct"])(a0, a1, a2));
	_LWLockAcquire = (Module["_LWLockAcquire"] = (a0: any, a1: any) =>
		(_LWLockAcquire = Module["_LWLockAcquire"] = wasmExports["LWLockAcquire"])(
			a0,
			a1,
		));
	_LWLockRelease = (Module["_LWLockRelease"] = (a0: any) =>
		(_LWLockRelease = Module["_LWLockRelease"] = wasmExports["LWLockRelease"])(
			a0,
		));
	_LWLockInitialize = (Module["_LWLockInitialize"] = (a0: any, a1: any) =>
		(_LWLockInitialize = Module["_LWLockInitialize"] =
			wasmExports["LWLockInitialize"])(a0, a1));
	_MemoryContextMemAllocated = (Module["_MemoryContextMemAllocated"] = (
		a0,
		a1,
	) =>
		(_MemoryContextMemAllocated = Module["_MemoryContextMemAllocated"] =
			wasmExports["MemoryContextMemAllocated"])(a0, a1));
	_GetCurrentCommandId = (Module["_GetCurrentCommandId"] = (a0: any) =>
		(_GetCurrentCommandId = Module["_GetCurrentCommandId"] =
			wasmExports["GetCurrentCommandId"])(a0));
	let _toast_open_indexes = (Module["_toast_open_indexes"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_toast_open_indexes = Module["_toast_open_indexes"] =
			wasmExports["toast_open_indexes"])(a0, a1, a2, a3));
	_RelationGetIndexList = (Module["_RelationGetIndexList"] = (a0: any) =>
		(_RelationGetIndexList = Module["_RelationGetIndexList"] =
			wasmExports["RelationGetIndexList"])(a0));
	let _systable_beginscan = (Module["_systable_beginscan"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_systable_beginscan = Module["_systable_beginscan"] =
			wasmExports["systable_beginscan"])(a0, a1, a2, a3, a4, a5));
	let _systable_getnext = (Module["_systable_getnext"] = (a0: any) =>
		(_systable_getnext = Module["_systable_getnext"] =
			wasmExports["systable_getnext"])(a0));
	let _systable_endscan = (Module["_systable_endscan"] = (a0: any) =>
		(_systable_endscan = Module["_systable_endscan"] =
			wasmExports["systable_endscan"])(a0));
	let _toast_close_indexes = (Module["_toast_close_indexes"] = (a0: any, a1: any, a2: any) =>
		(_toast_close_indexes = Module["_toast_close_indexes"] =
			wasmExports["toast_close_indexes"])(a0, a1, a2));
	let _systable_beginscan_ordered = (Module["_systable_beginscan_ordered"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_systable_beginscan_ordered = Module["_systable_beginscan_ordered"] =
			wasmExports["systable_beginscan_ordered"])(a0, a1, a2, a3, a4));
	let _systable_getnext_ordered = (Module["_systable_getnext_ordered"] = (
		a0,
		a1,
	) =>
		(_systable_getnext_ordered = Module["_systable_getnext_ordered"] =
			wasmExports["systable_getnext_ordered"])(a0, a1));
	let _systable_endscan_ordered = (Module["_systable_endscan_ordered"] = (a0: any) =>
		(_systable_endscan_ordered = Module["_systable_endscan_ordered"] =
			wasmExports["systable_endscan_ordered"])(a0));
	let _init_toast_snapshot = (Module["_init_toast_snapshot"] = (a0: any) =>
		(_init_toast_snapshot = Module["_init_toast_snapshot"] =
			wasmExports["init_toast_snapshot"])(a0));
	let _convert_tuples_by_position = (Module["_convert_tuples_by_position"] = (
		a0,
		a1,
		a2,
	) =>
		(_convert_tuples_by_position = Module["_convert_tuples_by_position"] =
			wasmExports["convert_tuples_by_position"])(a0, a1, a2));
	let _execute_attr_map_tuple = (Module["_execute_attr_map_tuple"] = (a0: any, a1: any) =>
		(_execute_attr_map_tuple = Module["_execute_attr_map_tuple"] =
			wasmExports["execute_attr_map_tuple"])(a0, a1));
	_ExecStoreVirtualTuple = (Module["_ExecStoreVirtualTuple"] = (a0: any) =>
		(_ExecStoreVirtualTuple = Module["_ExecStoreVirtualTuple"] =
			wasmExports["ExecStoreVirtualTuple"])(a0));
	let _bms_is_member = (Module["_bms_is_member"] = (a0: any, a1: any) =>
		(_bms_is_member = Module["_bms_is_member"] = wasmExports["bms_is_member"])(
			a0,
			a1,
		));
	let _bms_add_member = (Module["_bms_add_member"] = (a0: any, a1: any) =>
		(_bms_add_member = Module["_bms_add_member"] =
			wasmExports["bms_add_member"])(a0, a1));
	_CreateTupleDescCopy = (Module["_CreateTupleDescCopy"] = (a0: any) =>
		(_CreateTupleDescCopy = Module["_CreateTupleDescCopy"] =
			wasmExports["CreateTupleDescCopy"])(a0));
	_ResourceOwnerEnlarge = (Module["_ResourceOwnerEnlarge"] = (a0: any) =>
		(_ResourceOwnerEnlarge = Module["_ResourceOwnerEnlarge"] =
			wasmExports["ResourceOwnerEnlarge"])(a0));
	_ResourceOwnerRemember = (Module["_ResourceOwnerRemember"] = (
		a0,
		a1,
		a2,
	) =>
		(_ResourceOwnerRemember = Module["_ResourceOwnerRemember"] =
			wasmExports["ResourceOwnerRemember"])(a0, a1, a2));
	_DecrTupleDescRefCount = (Module["_DecrTupleDescRefCount"] = (a0: any) =>
		(_DecrTupleDescRefCount = Module["_DecrTupleDescRefCount"] =
			wasmExports["DecrTupleDescRefCount"])(a0));
	_ResourceOwnerForget = (Module["_ResourceOwnerForget"] = (a0: any, a1: any, a2: any) =>
		(_ResourceOwnerForget = Module["_ResourceOwnerForget"] =
			wasmExports["ResourceOwnerForget"])(a0, a1, a2));
	_datumIsEqual = (Module["_datumIsEqual"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_datumIsEqual = Module["_datumIsEqual"] = wasmExports["datumIsEqual"])(
			a0,
			a1,
			a2,
			a3,
		));
	_TupleDescInitEntryCollation = (Module["_TupleDescInitEntryCollation"] = (
		a0,
		a1,
		a2,
	) =>
		(_TupleDescInitEntryCollation = Module["_TupleDescInitEntryCollation"] =
			wasmExports["TupleDescInitEntryCollation"])(a0, a1, a2));
	_stringToNode = (Module["_stringToNode"] = (a0: any) =>
		(_stringToNode = Module["_stringToNode"] = wasmExports["stringToNode"])(
			a0,
		));
	let _pg_detoast_datum_copy = (Module["_pg_detoast_datum_copy"] = (a0: any) =>
		(_pg_detoast_datum_copy = Module["_pg_detoast_datum_copy"] =
			wasmExports["pg_detoast_datum_copy"])(a0));
	let _get_typlenbyvalalign = (Module["_get_typlenbyvalalign"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_get_typlenbyvalalign = Module["_get_typlenbyvalalign"] =
			wasmExports["get_typlenbyvalalign"])(a0, a1, a2, a3));
	let _deconstruct_array = (Module["_deconstruct_array"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_deconstruct_array = Module["_deconstruct_array"] =
			wasmExports["deconstruct_array"])(a0, a1, a2, a3, a4, a5, a6, a7));
	let _tbm_add_tuples = (Module["_tbm_add_tuples"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_tbm_add_tuples = Module["_tbm_add_tuples"] =
			wasmExports["tbm_add_tuples"])(a0, a1, a2, a3));
	_ginPostingListDecode = (Module["_ginPostingListDecode"] = (a0: any, a1: any) =>
		(_ginPostingListDecode = Module["_ginPostingListDecode"] =
			wasmExports["ginPostingListDecode"])(a0, a1));
	_ItemPointerCompare = (Module["_ItemPointerCompare"] = (a0: any, a1: any) =>
		(_ItemPointerCompare = Module["_ItemPointerCompare"] =
			wasmExports["ItemPointerCompare"])(a0, a1));
	_LockPage = (Module["_LockPage"] = (a0: any, a1: any, a2: any) =>
		(_LockPage = Module["_LockPage"] = wasmExports["LockPage"])(a0, a1, a2));
	_UnlockPage = (Module["_UnlockPage"] = (a0: any, a1: any, a2: any) =>
		(_UnlockPage = Module["_UnlockPage"] = wasmExports["UnlockPage"])(
			a0,
			a1,
			a2,
		));
	let _vacuum_delay_point = (Module["_vacuum_delay_point"] = () =>
		(_vacuum_delay_point = Module["_vacuum_delay_point"] =
			wasmExports["vacuum_delay_point"])());
	_RecordFreeIndexPage = (Module["_RecordFreeIndexPage"] = (a0: any, a1: any) =>
		(_RecordFreeIndexPage = Module["_RecordFreeIndexPage"] =
			wasmExports["RecordFreeIndexPage"])(a0, a1));
	_IndexFreeSpaceMapVacuum = (Module["_IndexFreeSpaceMapVacuum"] = (a0: any) =>
		(_IndexFreeSpaceMapVacuum = Module["_IndexFreeSpaceMapVacuum"] =
			wasmExports["IndexFreeSpaceMapVacuum"])(a0));
	let _log_newpage_range = (Module["_log_newpage_range"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_log_newpage_range = Module["_log_newpage_range"] =
			wasmExports["log_newpage_range"])(a0, a1, a2, a3, a4));
	_GetFreeIndexPage = (Module["_GetFreeIndexPage"] = (a0: any) =>
		(_GetFreeIndexPage = Module["_GetFreeIndexPage"] =
			wasmExports["GetFreeIndexPage"])(a0));
	_ConditionalLockBuffer = (Module["_ConditionalLockBuffer"] = (a0: any) =>
		(_ConditionalLockBuffer = Module["_ConditionalLockBuffer"] =
			wasmExports["ConditionalLockBuffer"])(a0));
	_LockBufferForCleanup = (Module["_LockBufferForCleanup"] = (a0: any) =>
		(_LockBufferForCleanup = Module["_LockBufferForCleanup"] =
			wasmExports["LockBufferForCleanup"])(a0));
	_gistcheckpage = (Module["_gistcheckpage"] = (a0: any, a1: any) =>
		(_gistcheckpage = Module["_gistcheckpage"] = wasmExports["gistcheckpage"])(
			a0,
			a1,
		));
	_PageIndexMultiDelete = (Module["_PageIndexMultiDelete"] = (a0: any, a1: any, a2: any) =>
		(_PageIndexMultiDelete = Module["_PageIndexMultiDelete"] =
			wasmExports["PageIndexMultiDelete"])(a0, a1, a2));
	_smgrnblocks = (Module["_smgrnblocks"] = (a0: any, a1: any) =>
		(_smgrnblocks = Module["_smgrnblocks"] = wasmExports["smgrnblocks"])(
			a0,
			a1,
		));
	let _list_free_deep = (Module["_list_free_deep"] = (a0: any) =>
		(_list_free_deep = Module["_list_free_deep"] =
			wasmExports["list_free_deep"])(a0));
	let _pairingheap_remove_first = (Module["_pairingheap_remove_first"] = (a0: any) =>
		(_pairingheap_remove_first = Module["_pairingheap_remove_first"] =
			wasmExports["pairingheap_remove_first"])(a0));
	let _pairingheap_add = (Module["_pairingheap_add"] = (a0: any, a1: any) =>
		(_pairingheap_add = Module["_pairingheap_add"] =
			wasmExports["pairingheap_add"])(a0, a1));
	let _float_overflow_error = (Module["_float_overflow_error"] = () =>
		(_float_overflow_error = Module["_float_overflow_error"] =
			wasmExports["float_overflow_error"])());
	let _float_underflow_error = (Module["_float_underflow_error"] = () =>
		(_float_underflow_error = Module["_float_underflow_error"] =
			wasmExports["float_underflow_error"])());
	_DirectFunctionCall5Coll = (Module["_DirectFunctionCall5Coll"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_DirectFunctionCall5Coll = Module["_DirectFunctionCall5Coll"] =
			wasmExports["DirectFunctionCall5Coll"])(a0, a1, a2, a3, a4, a5, a6));
	let _pairingheap_allocate = (Module["_pairingheap_allocate"] = (a0: any, a1: any) =>
		(_pairingheap_allocate = Module["_pairingheap_allocate"] =
			wasmExports["pairingheap_allocate"])(a0, a1));
	_GenerationContextCreate = (Module["_GenerationContextCreate"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_GenerationContextCreate = Module["_GenerationContextCreate"] =
			wasmExports["GenerationContextCreate"])(a0, a1, a2, a3, a4));
	let _pgstat_progress_update_param = (Module["_pgstat_progress_update_param"] =
		(a0: any, a1: any) =>
			(_pgstat_progress_update_param = Module["_pgstat_progress_update_param"] =
				wasmExports["pgstat_progress_update_param"])(a0, a1));
	let __hash_getbuf = (Module["__hash_getbuf"] = (a0: any, a1: any, a2: any, a3: any) =>
		(__hash_getbuf = Module["__hash_getbuf"] = wasmExports["_hash_getbuf"])(
			a0,
			a1,
			a2,
			a3,
		));
	let __hash_relbuf = (Module["__hash_relbuf"] = (a0: any, a1: any) =>
		(__hash_relbuf = Module["__hash_relbuf"] = wasmExports["_hash_relbuf"])(
			a0,
			a1,
		));
	let __hash_get_indextuple_hashkey = (Module["__hash_get_indextuple_hashkey"] =
		(a0: any) =>
			(__hash_get_indextuple_hashkey = Module["__hash_get_indextuple_hashkey"] =
				wasmExports["_hash_get_indextuple_hashkey"])(a0));
	let __hash_getbuf_with_strategy = (Module["__hash_getbuf_with_strategy"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(__hash_getbuf_with_strategy = Module["__hash_getbuf_with_strategy"] =
			wasmExports["_hash_getbuf_with_strategy"])(a0, a1, a2, a3, a4));
	let __hash_ovflblkno_to_bitno = (Module["__hash_ovflblkno_to_bitno"] = (
		a0,
		a1,
	) =>
		(__hash_ovflblkno_to_bitno = Module["__hash_ovflblkno_to_bitno"] =
			wasmExports["_hash_ovflblkno_to_bitno"])(a0, a1));
	let _list_member_oid = (Module["_list_member_oid"] = (a0: any, a1: any) =>
		(_list_member_oid = Module["_list_member_oid"] =
			wasmExports["list_member_oid"])(a0, a1));
	_HeapTupleSatisfiesVisibility = (Module["_HeapTupleSatisfiesVisibility"] =
		(a0: any, a1: any, a2: any) =>
			(_HeapTupleSatisfiesVisibility = Module["_HeapTupleSatisfiesVisibility"] =
				wasmExports["HeapTupleSatisfiesVisibility"])(a0, a1, a2));
	let _read_stream_begin_relation = (Module["_read_stream_begin_relation"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_read_stream_begin_relation = Module["_read_stream_begin_relation"] =
			wasmExports["read_stream_begin_relation"])(a0, a1, a2, a3, a4, a5, a6));
	_GetAccessStrategy = (Module["_GetAccessStrategy"] = (a0: any) =>
		(_GetAccessStrategy = Module["_GetAccessStrategy"] =
			wasmExports["GetAccessStrategy"])(a0));
	_FreeAccessStrategy = (Module["_FreeAccessStrategy"] = (a0: any) =>
		(_FreeAccessStrategy = Module["_FreeAccessStrategy"] =
			wasmExports["FreeAccessStrategy"])(a0));
	let _read_stream_end = (Module["_read_stream_end"] = (a0: any) =>
		(_read_stream_end = Module["_read_stream_end"] =
			wasmExports["read_stream_end"])(a0));
	let _heap_getnext = (Module["_heap_getnext"] = (a0: any, a1: any) =>
		(_heap_getnext = Module["_heap_getnext"] = wasmExports["heap_getnext"])(
			a0,
			a1,
		));
	_HeapTupleSatisfiesVacuum = (Module["_HeapTupleSatisfiesVacuum"] = (
		a0,
		a1,
		a2,
	) =>
		(_HeapTupleSatisfiesVacuum = Module["_HeapTupleSatisfiesVacuum"] =
			wasmExports["HeapTupleSatisfiesVacuum"])(a0, a1, a2));
	_GetMultiXactIdMembers = (Module["_GetMultiXactIdMembers"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_GetMultiXactIdMembers = Module["_GetMultiXactIdMembers"] =
			wasmExports["GetMultiXactIdMembers"])(a0, a1, a2, a3));
	_TransactionIdPrecedes = (Module["_TransactionIdPrecedes"] = (a0: any, a1: any) =>
		(_TransactionIdPrecedes = Module["_TransactionIdPrecedes"] =
			wasmExports["TransactionIdPrecedes"])(a0, a1));
	_HeapTupleGetUpdateXid = (Module["_HeapTupleGetUpdateXid"] = (a0: any) =>
		(_HeapTupleGetUpdateXid = Module["_HeapTupleGetUpdateXid"] =
			wasmExports["HeapTupleGetUpdateXid"])(a0));
	let _visibilitymap_clear = (Module["_visibilitymap_clear"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_visibilitymap_clear = Module["_visibilitymap_clear"] =
			wasmExports["visibilitymap_clear"])(a0, a1, a2, a3));
	let _pgstat_count_heap_insert = (Module["_pgstat_count_heap_insert"] = (
		a0,
		a1,
	) =>
		(_pgstat_count_heap_insert = Module["_pgstat_count_heap_insert"] =
			wasmExports["pgstat_count_heap_insert"])(a0, a1));
	_ExecFetchSlotHeapTuple = (Module["_ExecFetchSlotHeapTuple"] = (
		a0,
		a1,
		a2,
	) =>
		(_ExecFetchSlotHeapTuple = Module["_ExecFetchSlotHeapTuple"] =
			wasmExports["ExecFetchSlotHeapTuple"])(a0, a1, a2));
	_PageGetHeapFreeSpace = (Module["_PageGetHeapFreeSpace"] = (a0: any) =>
		(_PageGetHeapFreeSpace = Module["_PageGetHeapFreeSpace"] =
			wasmExports["PageGetHeapFreeSpace"])(a0));
	let _visibilitymap_pin = (Module["_visibilitymap_pin"] = (a0: any, a1: any, a2: any) =>
		(_visibilitymap_pin = Module["_visibilitymap_pin"] =
			wasmExports["visibilitymap_pin"])(a0, a1, a2));
	_HeapTupleSatisfiesUpdate = (Module["_HeapTupleSatisfiesUpdate"] = (
		a0,
		a1,
		a2,
	) =>
		(_HeapTupleSatisfiesUpdate = Module["_HeapTupleSatisfiesUpdate"] =
			wasmExports["HeapTupleSatisfiesUpdate"])(a0, a1, a2));
	_TransactionIdIsCurrentTransactionId = (Module[
		"_TransactionIdIsCurrentTransactionId"
	] = (a0: any) =>
		(_TransactionIdIsCurrentTransactionId = Module[
			"_TransactionIdIsCurrentTransactionId"
		] =
			wasmExports["TransactionIdIsCurrentTransactionId"])(a0));
	_TransactionIdDidCommit = (Module["_TransactionIdDidCommit"] = (a0: any) =>
		(_TransactionIdDidCommit = Module["_TransactionIdDidCommit"] =
			wasmExports["TransactionIdDidCommit"])(a0));
	_TransactionIdIsInProgress = (Module["_TransactionIdIsInProgress"] = (
		a0,
	) =>
		(_TransactionIdIsInProgress = Module["_TransactionIdIsInProgress"] =
			wasmExports["TransactionIdIsInProgress"])(a0));
	let _bms_free = (Module["_bms_free"] = (a0: any) =>
		(_bms_free = Module["_bms_free"] = wasmExports["bms_free"])(a0));
	let _bms_add_members = (Module["_bms_add_members"] = (a0: any, a1: any) =>
		(_bms_add_members = Module["_bms_add_members"] =
			wasmExports["bms_add_members"])(a0, a1));
	let _bms_next_member = (Module["_bms_next_member"] = (a0: any, a1: any) =>
		(_bms_next_member = Module["_bms_next_member"] =
			wasmExports["bms_next_member"])(a0, a1));
	let _bms_overlap = (Module["_bms_overlap"] = (a0: any, a1: any) =>
		(_bms_overlap = Module["_bms_overlap"] = wasmExports["bms_overlap"])(
			a0,
			a1,
		));
	_MultiXactIdPrecedes = (Module["_MultiXactIdPrecedes"] = (a0: any, a1: any) =>
		(_MultiXactIdPrecedes = Module["_MultiXactIdPrecedes"] =
			wasmExports["MultiXactIdPrecedes"])(a0, a1));
	let _heap_tuple_needs_eventual_freeze = (Module[
		"_heap_tuple_needs_eventual_freeze"
	] = (a0: any) =>
		(_heap_tuple_needs_eventual_freeze = Module[
			"_heap_tuple_needs_eventual_freeze"
		] =
			wasmExports["heap_tuple_needs_eventual_freeze"])(a0));
	_PrefetchBuffer = (Module["_PrefetchBuffer"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_PrefetchBuffer = Module["_PrefetchBuffer"] =
			wasmExports["PrefetchBuffer"])(a0, a1, a2, a3));
	_XLogRecGetBlockTagExtended = (Module["_XLogRecGetBlockTagExtended"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_XLogRecGetBlockTagExtended = Module["_XLogRecGetBlockTagExtended"] =
			wasmExports["XLogRecGetBlockTagExtended"])(a0, a1, a2, a3, a4, a5));
	let _read_stream_next_buffer = (Module["_read_stream_next_buffer"] = (
		a0,
		a1,
	) =>
		(_read_stream_next_buffer = Module["_read_stream_next_buffer"] =
			wasmExports["read_stream_next_buffer"])(a0, a1));
	_smgrexists = (Module["_smgrexists"] = (a0: any, a1: any) =>
		(_smgrexists = Module["_smgrexists"] = wasmExports["smgrexists"])(a0, a1));
	let _table_slot_create = (Module["_table_slot_create"] = (a0: any, a1: any) =>
		(_table_slot_create = Module["_table_slot_create"] =
			wasmExports["table_slot_create"])(a0, a1));
	_ExecDropSingleTupleTableSlot = (Module["_ExecDropSingleTupleTableSlot"] =
		(a0: any) =>
			(_ExecDropSingleTupleTableSlot = Module["_ExecDropSingleTupleTableSlot"] =
				wasmExports["ExecDropSingleTupleTableSlot"])(a0));
	_CreateExecutorState = (Module["_CreateExecutorState"] = () =>
		(_CreateExecutorState = Module["_CreateExecutorState"] =
			wasmExports["CreateExecutorState"])());
	_MakePerTupleExprContext = (Module["_MakePerTupleExprContext"] = (a0: any) =>
		(_MakePerTupleExprContext = Module["_MakePerTupleExprContext"] =
			wasmExports["MakePerTupleExprContext"])(a0));
	_GetOldestNonRemovableTransactionId = (Module[
		"_GetOldestNonRemovableTransactionId"
	] = (a0: any) =>
		(_GetOldestNonRemovableTransactionId = Module[
			"_GetOldestNonRemovableTransactionId"
		] =
			wasmExports["GetOldestNonRemovableTransactionId"])(a0));
	_FreeExecutorState = (Module["_FreeExecutorState"] = (a0: any) =>
		(_FreeExecutorState = Module["_FreeExecutorState"] =
			wasmExports["FreeExecutorState"])(a0));
	_MakeSingleTupleTableSlot = (Module["_MakeSingleTupleTableSlot"] = (
		a0,
		a1,
	) =>
		(_MakeSingleTupleTableSlot = Module["_MakeSingleTupleTableSlot"] =
			wasmExports["MakeSingleTupleTableSlot"])(a0, a1));
	_ExecStoreHeapTuple = (Module["_ExecStoreHeapTuple"] = (a0: any, a1: any, a2: any) =>
		(_ExecStoreHeapTuple = Module["_ExecStoreHeapTuple"] =
			wasmExports["ExecStoreHeapTuple"])(a0, a1, a2));
	let _visibilitymap_get_status = (Module["_visibilitymap_get_status"] = (
		a0,
		a1,
		a2,
	) =>
		(_visibilitymap_get_status = Module["_visibilitymap_get_status"] =
			wasmExports["visibilitymap_get_status"])(a0, a1, a2));
	_ExecStoreAllNullTuple = (Module["_ExecStoreAllNullTuple"] = (a0: any) =>
		(_ExecStoreAllNullTuple = Module["_ExecStoreAllNullTuple"] =
			wasmExports["ExecStoreAllNullTuple"])(a0));
	_XidInMVCCSnapshot = (Module["_XidInMVCCSnapshot"] = (a0: any, a1: any) =>
		(_XidInMVCCSnapshot = Module["_XidInMVCCSnapshot"] =
			wasmExports["XidInMVCCSnapshot"])(a0, a1));
	let _hash_seq_init = (Module["_hash_seq_init"] = (a0: any, a1: any) =>
		(_hash_seq_init = Module["_hash_seq_init"] = wasmExports["hash_seq_init"])(
			a0,
			a1,
		));
	let _hash_seq_search = (Module["_hash_seq_search"] = (a0: any) =>
		(_hash_seq_search = Module["_hash_seq_search"] =
			wasmExports["hash_seq_search"])(a0));
	_ftruncate = (Module["_ftruncate"] = (a0: any, a1: any) =>
		(_ftruncate = Module["_ftruncate"] = wasmExports["ftruncate"])(a0, a1));
	let _fd_fsync_fname = (Module["_fd_fsync_fname"] = (a0: any, a1: any) =>
		(_fd_fsync_fname = Module["_fd_fsync_fname"] =
			wasmExports["fd_fsync_fname"])(a0, a1));
	let _get_namespace_name = (Module["_get_namespace_name"] = (a0: any) =>
		(_get_namespace_name = Module["_get_namespace_name"] =
			wasmExports["get_namespace_name"])(a0));
	_GetRecordedFreeSpace = (Module["_GetRecordedFreeSpace"] = (a0: any, a1: any) =>
		(_GetRecordedFreeSpace = Module["_GetRecordedFreeSpace"] =
			wasmExports["GetRecordedFreeSpace"])(a0, a1));
	let _vac_estimate_reltuples = (Module["_vac_estimate_reltuples"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_vac_estimate_reltuples = Module["_vac_estimate_reltuples"] =
			wasmExports["vac_estimate_reltuples"])(a0, a1, a2, a3));
	_WaitLatch = (Module["_WaitLatch"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_WaitLatch = Module["_WaitLatch"] = wasmExports["WaitLatch"])(
			a0,
			a1,
			a2,
			a3,
		));
	_ResetLatch = (Module["_ResetLatch"] = (a0: any) =>
		(_ResetLatch = Module["_ResetLatch"] = wasmExports["ResetLatch"])(a0));
	let _clock_gettime = (Module["_clock_gettime"] = (a0: any, a1: any) =>
		(_clock_gettime = Module["_clock_gettime"] = wasmExports["clock_gettime"])(
			a0,
			a1,
		));
	_WalUsageAccumDiff = (Module["_WalUsageAccumDiff"] = (a0: any, a1: any, a2: any) =>
		(_WalUsageAccumDiff = Module["_WalUsageAccumDiff"] =
			wasmExports["WalUsageAccumDiff"])(a0, a1, a2));
	_BufferUsageAccumDiff = (Module["_BufferUsageAccumDiff"] = (a0: any, a1: any, a2: any) =>
		(_BufferUsageAccumDiff = Module["_BufferUsageAccumDiff"] =
			wasmExports["BufferUsageAccumDiff"])(a0, a1, a2));
	let _visibilitymap_prepare_truncate = (Module[
		"_visibilitymap_prepare_truncate"
	] = (a0: any, a1: any) =>
		(_visibilitymap_prepare_truncate = Module[
			"_visibilitymap_prepare_truncate"
		] =
			wasmExports["visibilitymap_prepare_truncate"])(a0, a1));
	let _pg_class_aclcheck = (Module["_pg_class_aclcheck"] = (a0: any, a1: any, a2: any) =>
		(_pg_class_aclcheck = Module["_pg_class_aclcheck"] =
			wasmExports["pg_class_aclcheck"])(a0, a1, a2));
	_btboolcmp = (Module["_btboolcmp"] = (a0: any) =>
		(_btboolcmp = Module["_btboolcmp"] = wasmExports["btboolcmp"])(a0));
	_btint2cmp = (Module["_btint2cmp"] = (a0: any) =>
		(_btint2cmp = Module["_btint2cmp"] = wasmExports["btint2cmp"])(a0));
	_btint4cmp = (Module["_btint4cmp"] = (a0: any) =>
		(_btint4cmp = Module["_btint4cmp"] = wasmExports["btint4cmp"])(a0));
	_btint8cmp = (Module["_btint8cmp"] = (a0: any) =>
		(_btint8cmp = Module["_btint8cmp"] = wasmExports["btint8cmp"])(a0));
	_btoidcmp = (Module["_btoidcmp"] = (a0: any) =>
		(_btoidcmp = Module["_btoidcmp"] = wasmExports["btoidcmp"])(a0));
	_btcharcmp = (Module["_btcharcmp"] = (a0: any) =>
		(_btcharcmp = Module["_btcharcmp"] = wasmExports["btcharcmp"])(a0));
	let __bt_form_posting = (Module["__bt_form_posting"] = (a0: any, a1: any, a2: any) =>
		(__bt_form_posting = Module["__bt_form_posting"] =
			wasmExports["_bt_form_posting"])(a0, a1, a2));
	let __bt_mkscankey = (Module["__bt_mkscankey"] = (a0: any, a1: any) =>
		(__bt_mkscankey = Module["__bt_mkscankey"] = wasmExports["_bt_mkscankey"])(
			a0,
			a1,
		));
	let __bt_checkpage = (Module["__bt_checkpage"] = (a0: any, a1: any) =>
		(__bt_checkpage = Module["__bt_checkpage"] = wasmExports["_bt_checkpage"])(
			a0,
			a1,
		));
	let __bt_compare = (Module["__bt_compare"] = (a0: any, a1: any, a2: any, a3: any) =>
		(__bt_compare = Module["__bt_compare"] = wasmExports["_bt_compare"])(
			a0,
			a1,
			a2,
			a3,
		));
	let __bt_relbuf = (Module["__bt_relbuf"] = (a0: any, a1: any) =>
		(__bt_relbuf = Module["__bt_relbuf"] = wasmExports["_bt_relbuf"])(a0, a1));
	let __bt_search = (Module["__bt_search"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(__bt_search = Module["__bt_search"] = wasmExports["_bt_search"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let __bt_binsrch_insert = (Module["__bt_binsrch_insert"] = (a0: any, a1: any) =>
		(__bt_binsrch_insert = Module["__bt_binsrch_insert"] =
			wasmExports["_bt_binsrch_insert"])(a0, a1));
	let __bt_freestack = (Module["__bt_freestack"] = (a0: any) =>
		(__bt_freestack = Module["__bt_freestack"] = wasmExports["_bt_freestack"])(
			a0,
		));
	let __bt_metaversion = (Module["__bt_metaversion"] = (a0: any, a1: any, a2: any) =>
		(__bt_metaversion = Module["__bt_metaversion"] =
			wasmExports["_bt_metaversion"])(a0, a1, a2));
	let __bt_allequalimage = (Module["__bt_allequalimage"] = (a0: any, a1: any) =>
		(__bt_allequalimage = Module["__bt_allequalimage"] =
			wasmExports["_bt_allequalimage"])(a0, a1));
	let _before_shmem_exit = (Module["_before_shmem_exit"] = (a0: any, a1: any) =>
		(_before_shmem_exit = Module["_before_shmem_exit"] =
			wasmExports["before_shmem_exit"])(a0, a1));
	let _cancel_before_shmem_exit = (Module["_cancel_before_shmem_exit"] = (
		a0,
		a1,
	) =>
		(_cancel_before_shmem_exit = Module["_cancel_before_shmem_exit"] =
			wasmExports["cancel_before_shmem_exit"])(a0, a1));
	let _pg_re_throw = (Module["_pg_re_throw"] = () =>
		(_pg_re_throw = Module["_pg_re_throw"] = wasmExports["pg_re_throw"])());
	let _get_opfamily_member = (Module["_get_opfamily_member"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_get_opfamily_member = Module["_get_opfamily_member"] =
			wasmExports["get_opfamily_member"])(a0, a1, a2, a3));
	let __bt_check_natts = (Module["__bt_check_natts"] = (a0: any, a1: any, a2: any, a3: any) =>
		(__bt_check_natts = Module["__bt_check_natts"] =
			wasmExports["_bt_check_natts"])(a0, a1, a2, a3));
	_strncpy = (Module["_strncpy"] = (a0: any, a1: any, a2: any) =>
		(_strncpy = Module["_strncpy"] = wasmExports["strncpy"])(a0, a1, a2));
	let _timestamptz_to_str = (Module["_timestamptz_to_str"] = (a0: any) =>
		(_timestamptz_to_str = Module["_timestamptz_to_str"] =
			wasmExports["timestamptz_to_str"])(a0));
	_XLogRecGetBlockRefInfo = (Module["_XLogRecGetBlockRefInfo"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_XLogRecGetBlockRefInfo = Module["_XLogRecGetBlockRefInfo"] =
			wasmExports["XLogRecGetBlockRefInfo"])(a0, a1, a2, a3, a4));
	let _varstr_cmp = (Module["_varstr_cmp"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_varstr_cmp = Module["_varstr_cmp"] = wasmExports["varstr_cmp"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_exprType = (Module["_exprType"] = (a0: any) =>
		(_exprType = Module["_exprType"] = wasmExports["exprType"])(a0));
	_GetActiveSnapshot = (Module["_GetActiveSnapshot"] = () =>
		(_GetActiveSnapshot = Module["_GetActiveSnapshot"] =
			wasmExports["GetActiveSnapshot"])());
	let _errdetail_relkind_not_supported = (Module[
		"_errdetail_relkind_not_supported"
	] = (a0: any) =>
		(_errdetail_relkind_not_supported = Module[
			"_errdetail_relkind_not_supported"
		] =
			wasmExports["errdetail_relkind_not_supported"])(a0));
	let _table_openrv = (Module["_table_openrv"] = (a0: any, a1: any) =>
		(_table_openrv = Module["_table_openrv"] = wasmExports["table_openrv"])(
			a0,
			a1,
		));
	let _table_slot_callbacks = (Module["_table_slot_callbacks"] = (a0: any) =>
		(_table_slot_callbacks = Module["_table_slot_callbacks"] =
			wasmExports["table_slot_callbacks"])(a0));
	let _clamp_row_est = (Module["_clamp_row_est"] = (a0: any) =>
		(_clamp_row_est = Module["_clamp_row_est"] = wasmExports["clamp_row_est"])(
			a0,
		));
	let _estimate_expression_value = (Module["_estimate_expression_value"] = (
		a0,
		a1,
	) =>
		(_estimate_expression_value = Module["_estimate_expression_value"] =
			wasmExports["estimate_expression_value"])(a0, a1));
	_XLogFlush = (Module["_XLogFlush"] = (a0: any) =>
		(_XLogFlush = Module["_XLogFlush"] = wasmExports["XLogFlush"])(a0));
	let _get_call_result_type = (Module["_get_call_result_type"] = (a0: any, a1: any, a2: any) =>
		(_get_call_result_type = Module["_get_call_result_type"] =
			wasmExports["get_call_result_type"])(a0, a1, a2));
	_HeapTupleHeaderGetDatum = (Module["_HeapTupleHeaderGetDatum"] = (a0: any) =>
		(_HeapTupleHeaderGetDatum = Module["_HeapTupleHeaderGetDatum"] =
			wasmExports["HeapTupleHeaderGetDatum"])(a0));
	_GenericXLogStart = (Module["_GenericXLogStart"] = (a0: any) =>
		(_GenericXLogStart = Module["_GenericXLogStart"] =
			wasmExports["GenericXLogStart"])(a0));
	_GenericXLogRegisterBuffer = (Module["_GenericXLogRegisterBuffer"] = (
		a0,
		a1,
		a2,
	) =>
		(_GenericXLogRegisterBuffer = Module["_GenericXLogRegisterBuffer"] =
			wasmExports["GenericXLogRegisterBuffer"])(a0, a1, a2));
	_GenericXLogFinish = (Module["_GenericXLogFinish"] = (a0: any) =>
		(_GenericXLogFinish = Module["_GenericXLogFinish"] =
			wasmExports["GenericXLogFinish"])(a0));
	_GenericXLogAbort = (Module["_GenericXLogAbort"] = (a0: any) =>
		(_GenericXLogAbort = Module["_GenericXLogAbort"] =
			wasmExports["GenericXLogAbort"])(a0));
	let _errmsg_plural = (Module["_errmsg_plural"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_errmsg_plural = Module["_errmsg_plural"] = wasmExports["errmsg_plural"])(
			a0,
			a1,
			a2,
			a3,
		));
	_ReadNextMultiXactId = (Module["_ReadNextMultiXactId"] = () =>
		(_ReadNextMultiXactId = Module["_ReadNextMultiXactId"] =
			wasmExports["ReadNextMultiXactId"])());
	_ReadMultiXactIdRange = (Module["_ReadMultiXactIdRange"] = (a0: any, a1: any) =>
		(_ReadMultiXactIdRange = Module["_ReadMultiXactIdRange"] =
			wasmExports["ReadMultiXactIdRange"])(a0, a1));
	_MultiXactIdPrecedesOrEquals = (Module["_MultiXactIdPrecedesOrEquals"] = (
		a0,
		a1,
	) =>
		(_MultiXactIdPrecedesOrEquals = Module["_MultiXactIdPrecedesOrEquals"] =
			wasmExports["MultiXactIdPrecedesOrEquals"])(a0, a1));
	let _init_MultiFuncCall = (Module["_init_MultiFuncCall"] = (a0: any) =>
		(_init_MultiFuncCall = Module["_init_MultiFuncCall"] =
			wasmExports["init_MultiFuncCall"])(a0));
	_TupleDescGetAttInMetadata = (Module["_TupleDescGetAttInMetadata"] = (
		a0,
	) =>
		(_TupleDescGetAttInMetadata = Module["_TupleDescGetAttInMetadata"] =
			wasmExports["TupleDescGetAttInMetadata"])(a0));
	let _per_MultiFuncCall = (Module["_per_MultiFuncCall"] = (a0: any) =>
		(_per_MultiFuncCall = Module["_per_MultiFuncCall"] =
			wasmExports["per_MultiFuncCall"])(a0));
	_BuildTupleFromCStrings = (Module["_BuildTupleFromCStrings"] = (a0: any, a1: any) =>
		(_BuildTupleFromCStrings = Module["_BuildTupleFromCStrings"] =
			wasmExports["BuildTupleFromCStrings"])(a0, a1));
	let _end_MultiFuncCall = (Module["_end_MultiFuncCall"] = (a0: any, a1: any) =>
		(_end_MultiFuncCall = Module["_end_MultiFuncCall"] =
			wasmExports["end_MultiFuncCall"])(a0, a1));
	_GetCurrentSubTransactionId = (Module["_GetCurrentSubTransactionId"] =
		() =>
			(_GetCurrentSubTransactionId = Module["_GetCurrentSubTransactionId"] =
				wasmExports["GetCurrentSubTransactionId"])());
	_WaitForBackgroundWorkerShutdown = (Module[
		"_WaitForBackgroundWorkerShutdown"
	] = (a0: any) =>
		(_WaitForBackgroundWorkerShutdown = Module[
			"_WaitForBackgroundWorkerShutdown"
		] =
			wasmExports["WaitForBackgroundWorkerShutdown"])(a0));
	_RegisterDynamicBackgroundWorker = (Module[
		"_RegisterDynamicBackgroundWorker"
	] = (a0: any, a1: any) =>
		(_RegisterDynamicBackgroundWorker = Module[
			"_RegisterDynamicBackgroundWorker"
		] =
			wasmExports["RegisterDynamicBackgroundWorker"])(a0, a1));
	_BackgroundWorkerUnblockSignals = (Module[
		"_BackgroundWorkerUnblockSignals"
	] = () =>
		(_BackgroundWorkerUnblockSignals = Module[
			"_BackgroundWorkerUnblockSignals"
		] =
			wasmExports["BackgroundWorkerUnblockSignals"])());
	_BackgroundWorkerInitializeConnectionByOid = (Module[
		"_BackgroundWorkerInitializeConnectionByOid"
	] = (a0: any, a1: any, a2: any) =>
		(_BackgroundWorkerInitializeConnectionByOid = Module[
			"_BackgroundWorkerInitializeConnectionByOid"
		] =
			wasmExports["BackgroundWorkerInitializeConnectionByOid"])(a0, a1, a2));
	_GetDatabaseEncoding = (Module["_GetDatabaseEncoding"] = () =>
		(_GetDatabaseEncoding = Module["_GetDatabaseEncoding"] =
			wasmExports["GetDatabaseEncoding"])());
	_RmgrNotFound = (Module["_RmgrNotFound"] = (a0: any) =>
		(_RmgrNotFound = Module["_RmgrNotFound"] = wasmExports["RmgrNotFound"])(
			a0,
		));
	_InitMaterializedSRF = (Module["_InitMaterializedSRF"] = (a0: any, a1: any) =>
		(_InitMaterializedSRF = Module["_InitMaterializedSRF"] =
			wasmExports["InitMaterializedSRF"])(a0, a1));
	let _tuplestore_putvalues = (Module["_tuplestore_putvalues"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_tuplestore_putvalues = Module["_tuplestore_putvalues"] =
			wasmExports["tuplestore_putvalues"])(a0, a1, a2, a3));
	_AllocateFile = (Module["_AllocateFile"] = (a0: any, a1: any) =>
		(_AllocateFile = Module["_AllocateFile"] = wasmExports["AllocateFile"])(
			a0,
			a1,
		));
	_FreeFile = (Module["_FreeFile"] = (a0: any) =>
		(_FreeFile = Module["_FreeFile"] = wasmExports["FreeFile"])(a0));
	let _fd_durable_rename = (Module["_fd_durable_rename"] = (a0: any, a1: any, a2: any) =>
		(_fd_durable_rename = Module["_fd_durable_rename"] =
			wasmExports["fd_durable_rename"])(a0, a1, a2));
	_BlessTupleDesc = (Module["_BlessTupleDesc"] = (a0: any) =>
		(_BlessTupleDesc = Module["_BlessTupleDesc"] =
			wasmExports["BlessTupleDesc"])(a0));
	_fstat = (Module["_fstat"] = (a0: any, a1: any) =>
		(_fstat = Module["_fstat"] = wasmExports["fstat"])(a0, a1));
	let _superuser_arg = (Module["_superuser_arg"] = (a0: any) =>
		(_superuser_arg = Module["_superuser_arg"] = wasmExports["superuser_arg"])(
			a0,
		));
	let _wal_segment_close = (Module["_wal_segment_close"] = (a0: any) =>
		(_wal_segment_close = Module["_wal_segment_close"] =
			wasmExports["wal_segment_close"])(a0));
	let _wal_segment_open = (Module["_wal_segment_open"] = (a0: any, a1: any, a2: any) =>
		(_wal_segment_open = Module["_wal_segment_open"] =
			wasmExports["wal_segment_open"])(a0, a1, a2));
	_XLogReaderAllocate = (Module["_XLogReaderAllocate"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_XLogReaderAllocate = Module["_XLogReaderAllocate"] =
			wasmExports["XLogReaderAllocate"])(a0, a1, a2, a3));
	_XLogReadRecord = (Module["_XLogReadRecord"] = (a0: any, a1: any) =>
		(_XLogReadRecord = Module["_XLogReadRecord"] =
			wasmExports["XLogReadRecord"])(a0, a1));
	_XLogReaderFree = (Module["_XLogReaderFree"] = (a0: any) =>
		(_XLogReaderFree = Module["_XLogReaderFree"] =
			wasmExports["XLogReaderFree"])(a0));
	_GetTopFullTransactionId = (Module["_GetTopFullTransactionId"] = () =>
		(_GetTopFullTransactionId = Module["_GetTopFullTransactionId"] =
			wasmExports["GetTopFullTransactionId"])());
	_GetCurrentTransactionNestLevel = (Module[
		"_GetCurrentTransactionNestLevel"
	] = () =>
		(_GetCurrentTransactionNestLevel = Module[
			"_GetCurrentTransactionNestLevel"
		] =
			wasmExports["GetCurrentTransactionNestLevel"])());
	_ResourceOwnerCreate = (Module["_ResourceOwnerCreate"] = (a0: any, a1: any) =>
		(_ResourceOwnerCreate = Module["_ResourceOwnerCreate"] =
			wasmExports["ResourceOwnerCreate"])(a0, a1));
	_RegisterXactCallback = (Module["_RegisterXactCallback"] = (a0: any, a1: any) =>
		(_RegisterXactCallback = Module["_RegisterXactCallback"] =
			wasmExports["RegisterXactCallback"])(a0, a1));
	_RegisterSubXactCallback = (Module["_RegisterSubXactCallback"] = (
		a0,
		a1,
	) =>
		(_RegisterSubXactCallback = Module["_RegisterSubXactCallback"] =
			wasmExports["RegisterSubXactCallback"])(a0, a1));
	_BeginInternalSubTransaction = (Module["_BeginInternalSubTransaction"] = (
		a0,
	) =>
		(_BeginInternalSubTransaction = Module["_BeginInternalSubTransaction"] =
			wasmExports["BeginInternalSubTransaction"])(a0));
	_ReleaseCurrentSubTransaction = (Module["_ReleaseCurrentSubTransaction"] =
		() =>
			(_ReleaseCurrentSubTransaction = Module["_ReleaseCurrentSubTransaction"] =
				wasmExports["ReleaseCurrentSubTransaction"])());
	_ResourceOwnerDelete = (Module["_ResourceOwnerDelete"] = (a0: any) =>
		(_ResourceOwnerDelete = Module["_ResourceOwnerDelete"] =
			wasmExports["ResourceOwnerDelete"])(a0));
	_RollbackAndReleaseCurrentSubTransaction = (Module[
		"_RollbackAndReleaseCurrentSubTransaction"
	] = () =>
		(_RollbackAndReleaseCurrentSubTransaction = Module[
			"_RollbackAndReleaseCurrentSubTransaction"
		] =
			wasmExports["RollbackAndReleaseCurrentSubTransaction"])());
	_ReleaseExternalFD = (Module["_ReleaseExternalFD"] = () =>
		(_ReleaseExternalFD = Module["_ReleaseExternalFD"] =
			wasmExports["ReleaseExternalFD"])());
	_GetFlushRecPtr = (Module["_GetFlushRecPtr"] = (a0: any) =>
		(_GetFlushRecPtr = Module["_GetFlushRecPtr"] =
			wasmExports["GetFlushRecPtr"])(a0));
	_GetXLogReplayRecPtr = (Module["_GetXLogReplayRecPtr"] = (a0: any) =>
		(_GetXLogReplayRecPtr = Module["_GetXLogReplayRecPtr"] =
			wasmExports["GetXLogReplayRecPtr"])(a0));
	_TimestampDifferenceMilliseconds = (Module[
		"_TimestampDifferenceMilliseconds"
	] = (a0: any, a1: any) =>
		(_TimestampDifferenceMilliseconds = Module[
			"_TimestampDifferenceMilliseconds"
		] =
			wasmExports["TimestampDifferenceMilliseconds"])(a0, a1));
	let _numeric_in = (Module["_numeric_in"] = (a0: any) =>
		(_numeric_in = Module["_numeric_in"] = wasmExports["numeric_in"])(a0));
	_DirectFunctionCall3Coll = (Module["_DirectFunctionCall3Coll"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_DirectFunctionCall3Coll = Module["_DirectFunctionCall3Coll"] =
			wasmExports["DirectFunctionCall3Coll"])(a0, a1, a2, a3, a4));
	_XLogFindNextRecord = (Module["_XLogFindNextRecord"] = (a0: any, a1: any) =>
		(_XLogFindNextRecord = Module["_XLogFindNextRecord"] =
			wasmExports["XLogFindNextRecord"])(a0, a1));
	_RestoreBlockImage = (Module["_RestoreBlockImage"] = (a0: any, a1: any, a2: any) =>
		(_RestoreBlockImage = Module["_RestoreBlockImage"] =
			wasmExports["RestoreBlockImage"])(a0, a1, a2));
	let _timestamptz_in = (Module["_timestamptz_in"] = (a0: any) =>
		(_timestamptz_in = Module["_timestamptz_in"] =
			wasmExports["timestamptz_in"])(a0));
	_fscanf = (Module["_fscanf"] = (a0: any, a1: any, a2: any) =>
		(_fscanf = Module["_fscanf"] = wasmExports["fscanf"])(a0, a1, a2));
	_XLogRecStoreStats = (Module["_XLogRecStoreStats"] = (a0: any, a1: any) =>
		(_XLogRecStoreStats = Module["_XLogRecStoreStats"] =
			wasmExports["XLogRecStoreStats"])(a0, a1));
	let _hash_get_num_entries = (Module["_hash_get_num_entries"] = (a0: any) =>
		(_hash_get_num_entries = Module["_hash_get_num_entries"] =
			wasmExports["hash_get_num_entries"])(a0));
	let _read_local_xlog_page_no_wait = (Module["_read_local_xlog_page_no_wait"] =
		(a0: any, a1: any, a2: any, a3: any, a4: any) =>
			(_read_local_xlog_page_no_wait = Module["_read_local_xlog_page_no_wait"] =
				wasmExports["read_local_xlog_page_no_wait"])(a0, a1, a2, a3, a4));
	let _escape_json = (Module["_escape_json"] = (a0: any, a1: any) =>
		(_escape_json = Module["_escape_json"] = wasmExports["escape_json"])(
			a0,
			a1,
		));
	let _list_sort = (Module["_list_sort"] = (a0: any, a1: any) =>
		(_list_sort = Module["_list_sort"] = wasmExports["list_sort"])(a0, a1));
	_getegid = (Module["_getegid"] = () =>
		(_getegid = Module["_getegid"] = wasmExports["getegid"])());
	let _pg_checksum_page = (Module["_pg_checksum_page"] = (a0: any, a1: any) =>
		(_pg_checksum_page = Module["_pg_checksum_page"] =
			wasmExports["pg_checksum_page"])(a0, a1));
	let _deflateInit2_ = (Module["_deflateInit2_"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_deflateInit2_ = Module["_deflateInit2_"] = wasmExports["deflateInit2_"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
		));
	_deflate = (Module["_deflate"] = (a0: any, a1: any) =>
		(_deflate = Module["_deflate"] = wasmExports["deflate"])(a0, a1));
	let _bbsink_forward_end_archive = (Module["_bbsink_forward_end_archive"] = (
		a0,
	) =>
		(_bbsink_forward_end_archive = Module["_bbsink_forward_end_archive"] =
			wasmExports["bbsink_forward_end_archive"])(a0));
	let _bbsink_forward_begin_manifest = (Module[
		"_bbsink_forward_begin_manifest"
	] = (a0: any) =>
		(_bbsink_forward_begin_manifest = Module["_bbsink_forward_begin_manifest"] =
			wasmExports["bbsink_forward_begin_manifest"])(a0));
	let _bbsink_forward_end_manifest = (Module["_bbsink_forward_end_manifest"] = (
		a0,
	) =>
		(_bbsink_forward_end_manifest = Module["_bbsink_forward_end_manifest"] =
			wasmExports["bbsink_forward_end_manifest"])(a0));
	let _bbsink_forward_end_backup = (Module["_bbsink_forward_end_backup"] = (
		a0,
		a1,
		a2,
	) =>
		(_bbsink_forward_end_backup = Module["_bbsink_forward_end_backup"] =
			wasmExports["bbsink_forward_end_backup"])(a0, a1, a2));
	let _bbsink_forward_cleanup = (Module["_bbsink_forward_cleanup"] = (a0: any) =>
		(_bbsink_forward_cleanup = Module["_bbsink_forward_cleanup"] =
			wasmExports["bbsink_forward_cleanup"])(a0));
	let _list_concat = (Module["_list_concat"] = (a0: any, a1: any) =>
		(_list_concat = Module["_list_concat"] = wasmExports["list_concat"])(
			a0,
			a1,
		));
	let _bbsink_forward_begin_backup = (Module["_bbsink_forward_begin_backup"] = (
		a0,
	) =>
		(_bbsink_forward_begin_backup = Module["_bbsink_forward_begin_backup"] =
			wasmExports["bbsink_forward_begin_backup"])(a0));
	let _bbsink_forward_archive_contents = (Module[
		"_bbsink_forward_archive_contents"
	] = (a0: any, a1: any) =>
		(_bbsink_forward_archive_contents = Module[
			"_bbsink_forward_archive_contents"
		] =
			wasmExports["bbsink_forward_archive_contents"])(a0, a1));
	let _bbsink_forward_begin_archive = (Module["_bbsink_forward_begin_archive"] =
		(a0: any, a1: any) =>
			(_bbsink_forward_begin_archive = Module["_bbsink_forward_begin_archive"] =
				wasmExports["bbsink_forward_begin_archive"])(a0, a1));
	let _bbsink_forward_manifest_contents = (Module[
		"_bbsink_forward_manifest_contents"
	] = (a0: any, a1: any) =>
		(_bbsink_forward_manifest_contents = Module[
			"_bbsink_forward_manifest_contents"
		] =
			wasmExports["bbsink_forward_manifest_contents"])(a0, a1));
	let _has_privs_of_role = (Module["_has_privs_of_role"] = (a0: any, a1: any) =>
		(_has_privs_of_role = Module["_has_privs_of_role"] =
			wasmExports["has_privs_of_role"])(a0, a1));
	_BaseBackupAddTarget = (Module["_BaseBackupAddTarget"] = (a0: any, a1: any, a2: any) =>
		(_BaseBackupAddTarget = Module["_BaseBackupAddTarget"] =
			wasmExports["BaseBackupAddTarget"])(a0, a1, a2));
	let _list_copy = (Module["_list_copy"] = (a0: any) =>
		(_list_copy = Module["_list_copy"] = wasmExports["list_copy"])(a0));
	let _tuplestore_puttuple = (Module["_tuplestore_puttuple"] = (a0: any, a1: any) =>
		(_tuplestore_puttuple = Module["_tuplestore_puttuple"] =
			wasmExports["tuplestore_puttuple"])(a0, a1));
	_makeRangeVar = (Module["_makeRangeVar"] = (a0: any, a1: any, a2: any) =>
		(_makeRangeVar = Module["_makeRangeVar"] = wasmExports["makeRangeVar"])(
			a0,
			a1,
			a2,
		));
	_DefineIndex = (Module["_DefineIndex"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
		a11,
	) =>
		(_DefineIndex = Module["_DefineIndex"] = wasmExports["DefineIndex"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
			a11,
		));
	_fread = (Module["_fread"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_fread = Module["_fread"] = wasmExports["fread"])(a0, a1, a2, a3));
	_clearerr = (Module["_clearerr"] = (a0: any) =>
		(_clearerr = Module["_clearerr"] = wasmExports["clearerr"])(a0));
	_copyObjectImpl = (Module["_copyObjectImpl"] = (a0: any) =>
		(_copyObjectImpl = Module["_copyObjectImpl"] =
			wasmExports["copyObjectImpl"])(a0));
	let _lappend_oid = (Module["_lappend_oid"] = (a0: any, a1: any) =>
		(_lappend_oid = Module["_lappend_oid"] = wasmExports["lappend_oid"])(
			a0,
			a1,
		));
	_makeTypeNameFromNameList = (Module["_makeTypeNameFromNameList"] = (a0: any) =>
		(_makeTypeNameFromNameList = Module["_makeTypeNameFromNameList"] =
			wasmExports["makeTypeNameFromNameList"])(a0));
	_CatalogTupleUpdate = (Module["_CatalogTupleUpdate"] = (a0: any, a1: any, a2: any) =>
		(_CatalogTupleUpdate = Module["_CatalogTupleUpdate"] =
			wasmExports["CatalogTupleUpdate"])(a0, a1, a2));
	let _get_rel_name = (Module["_get_rel_name"] = (a0: any) =>
		(_get_rel_name = Module["_get_rel_name"] = wasmExports["get_rel_name"])(
			a0,
		));
	_CatalogTupleDelete = (Module["_CatalogTupleDelete"] = (a0: any, a1: any) =>
		(_CatalogTupleDelete = Module["_CatalogTupleDelete"] =
			wasmExports["CatalogTupleDelete"])(a0, a1));
	_CatalogTupleInsert = (Module["_CatalogTupleInsert"] = (a0: any, a1: any) =>
		(_CatalogTupleInsert = Module["_CatalogTupleInsert"] =
			wasmExports["CatalogTupleInsert"])(a0, a1));
	_recordDependencyOn = (Module["_recordDependencyOn"] = (a0: any, a1: any, a2: any) =>
		(_recordDependencyOn = Module["_recordDependencyOn"] =
			wasmExports["recordDependencyOn"])(a0, a1, a2));
	let _get_element_type = (Module["_get_element_type"] = (a0: any) =>
		(_get_element_type = Module["_get_element_type"] =
			wasmExports["get_element_type"])(a0));
	let _object_aclcheck = (Module["_object_aclcheck"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_object_aclcheck = Module["_object_aclcheck"] =
			wasmExports["object_aclcheck"])(a0, a1, a2, a3));
	_superuser = (Module["_superuser"] = () =>
		(_superuser = Module["_superuser"] = wasmExports["superuser"])());
	_SearchSysCacheAttName = (Module["_SearchSysCacheAttName"] = (a0: any, a1: any) =>
		(_SearchSysCacheAttName = Module["_SearchSysCacheAttName"] =
			wasmExports["SearchSysCacheAttName"])(a0, a1));
	let _new_object_addresses = (Module["_new_object_addresses"] = () =>
		(_new_object_addresses = Module["_new_object_addresses"] =
			wasmExports["new_object_addresses"])());
	let _free_object_addresses = (Module["_free_object_addresses"] = (a0: any) =>
		(_free_object_addresses = Module["_free_object_addresses"] =
			wasmExports["free_object_addresses"])(a0));
	_performMultipleDeletions = (Module["_performMultipleDeletions"] = (
		a0,
		a1,
		a2,
	) =>
		(_performMultipleDeletions = Module["_performMultipleDeletions"] =
			wasmExports["performMultipleDeletions"])(a0, a1, a2));
	_recordDependencyOnExpr = (Module["_recordDependencyOnExpr"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_recordDependencyOnExpr = Module["_recordDependencyOnExpr"] =
			wasmExports["recordDependencyOnExpr"])(a0, a1, a2, a3));
	let _query_tree_walker_impl = (Module["_query_tree_walker_impl"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_query_tree_walker_impl = Module["_query_tree_walker_impl"] =
			wasmExports["query_tree_walker_impl"])(a0, a1, a2, a3));
	let _expression_tree_walker_impl = (Module["_expression_tree_walker_impl"] = (
		a0,
		a1,
		a2,
	) =>
		(_expression_tree_walker_impl = Module["_expression_tree_walker_impl"] =
			wasmExports["expression_tree_walker_impl"])(a0, a1, a2));
	let _add_exact_object_address = (Module["_add_exact_object_address"] = (
		a0,
		a1,
	) =>
		(_add_exact_object_address = Module["_add_exact_object_address"] =
			wasmExports["add_exact_object_address"])(a0, a1));
	let _get_rel_relkind = (Module["_get_rel_relkind"] = (a0: any) =>
		(_get_rel_relkind = Module["_get_rel_relkind"] =
			wasmExports["get_rel_relkind"])(a0));
	let _get_typtype = (Module["_get_typtype"] = (a0: any) =>
		(_get_typtype = Module["_get_typtype"] = wasmExports["get_typtype"])(a0));
	let _list_delete_last = (Module["_list_delete_last"] = (a0: any) =>
		(_list_delete_last = Module["_list_delete_last"] =
			wasmExports["list_delete_last"])(a0));
	let _type_is_collatable = (Module["_type_is_collatable"] = (a0: any) =>
		(_type_is_collatable = Module["_type_is_collatable"] =
			wasmExports["type_is_collatable"])(a0));
	_GetSysCacheOid = (Module["_GetSysCacheOid"] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_GetSysCacheOid = Module["_GetSysCacheOid"] =
			wasmExports["GetSysCacheOid"])(a0, a1, a2, a3, a4, a5));
	_CheckTableNotInUse = (Module["_CheckTableNotInUse"] = (a0: any, a1: any) =>
		(_CheckTableNotInUse = Module["_CheckTableNotInUse"] =
			wasmExports["CheckTableNotInUse"])(a0, a1));
	let _construct_array = (Module["_construct_array"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_construct_array = Module["_construct_array"] =
			wasmExports["construct_array"])(a0, a1, a2, a3, a4, a5));
	let _make_parsestate = (Module["_make_parsestate"] = (a0: any) =>
		(_make_parsestate = Module["_make_parsestate"] =
			wasmExports["make_parsestate"])(a0));
	_transformExpr = (Module["_transformExpr"] = (a0: any, a1: any, a2: any) =>
		(_transformExpr = Module["_transformExpr"] = wasmExports["transformExpr"])(
			a0,
			a1,
			a2,
		));
	_equal = (Module["_equal"] = (a0: any, a1: any) =>
		(_equal = Module["_equal"] = wasmExports["equal"])(a0, a1));
	let _pull_var_clause = (Module["_pull_var_clause"] = (a0: any, a1: any) =>
		(_pull_var_clause = Module["_pull_var_clause"] =
			wasmExports["pull_var_clause"])(a0, a1));
	let _get_attname = (Module["_get_attname"] = (a0: any, a1: any, a2: any) =>
		(_get_attname = Module["_get_attname"] = wasmExports["get_attname"])(
			a0,
			a1,
			a2,
		));
	let _coerce_to_target_type = (Module["_coerce_to_target_type"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_coerce_to_target_type = Module["_coerce_to_target_type"] =
			wasmExports["coerce_to_target_type"])(a0, a1, a2, a3, a4, a5, a6, a7));
	_nodeToString = (Module["_nodeToString"] = (a0: any) =>
		(_nodeToString = Module["_nodeToString"] = wasmExports["nodeToString"])(
			a0,
		));
	let _parser_errposition = (Module["_parser_errposition"] = (a0: any, a1: any) =>
		(_parser_errposition = Module["_parser_errposition"] =
			wasmExports["parser_errposition"])(a0, a1));
	_exprTypmod = (Module["_exprTypmod"] = (a0: any) =>
		(_exprTypmod = Module["_exprTypmod"] = wasmExports["exprTypmod"])(a0));
	let _get_base_element_type = (Module["_get_base_element_type"] = (a0: any) =>
		(_get_base_element_type = Module["_get_base_element_type"] =
			wasmExports["get_base_element_type"])(a0));
	_SystemFuncName = (Module["_SystemFuncName"] = (a0: any) =>
		(_SystemFuncName = Module["_SystemFuncName"] =
			wasmExports["SystemFuncName"])(a0));
	_CreateTrigger = (Module["_CreateTrigger"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
		a11,
	) =>
		(_CreateTrigger = Module["_CreateTrigger"] = wasmExports["CreateTrigger"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
			a11,
		));
	let _plan_create_index_workers = (Module["_plan_create_index_workers"] = (
		a0,
		a1,
	) =>
		(_plan_create_index_workers = Module["_plan_create_index_workers"] =
			wasmExports["plan_create_index_workers"])(a0, a1));
	let _get_rel_namespace = (Module["_get_rel_namespace"] = (a0: any) =>
		(_get_rel_namespace = Module["_get_rel_namespace"] =
			wasmExports["get_rel_namespace"])(a0));
	_ConditionalLockRelationOid = (Module["_ConditionalLockRelationOid"] = (
		a0,
		a1,
	) =>
		(_ConditionalLockRelationOid = Module["_ConditionalLockRelationOid"] =
			wasmExports["ConditionalLockRelationOid"])(a0, a1));
	_RelnameGetRelid = (Module["_RelnameGetRelid"] = (a0: any) =>
		(_RelnameGetRelid = Module["_RelnameGetRelid"] =
			wasmExports["RelnameGetRelid"])(a0));
	let _get_relkind_objtype = (Module["_get_relkind_objtype"] = (a0: any) =>
		(_get_relkind_objtype = Module["_get_relkind_objtype"] =
			wasmExports["get_relkind_objtype"])(a0));
	_RelationIsVisible = (Module["_RelationIsVisible"] = (a0: any) =>
		(_RelationIsVisible = Module["_RelationIsVisible"] =
			wasmExports["RelationIsVisible"])(a0));
	let _get_func_arg_info = (Module["_get_func_arg_info"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_get_func_arg_info = Module["_get_func_arg_info"] =
			wasmExports["get_func_arg_info"])(a0, a1, a2, a3));
	_NameListToString = (Module["_NameListToString"] = (a0: any) =>
		(_NameListToString = Module["_NameListToString"] =
			wasmExports["NameListToString"])(a0));
	_OpernameGetOprid = (Module["_OpernameGetOprid"] = (a0: any, a1: any, a2: any) =>
		(_OpernameGetOprid = Module["_OpernameGetOprid"] =
			wasmExports["OpernameGetOprid"])(a0, a1, a2));
	_makeRangeVarFromNameList = (Module["_makeRangeVarFromNameList"] = (a0: any) =>
		(_makeRangeVarFromNameList = Module["_makeRangeVarFromNameList"] =
			wasmExports["makeRangeVarFromNameList"])(a0));
	let _quote_identifier = (Module["_quote_identifier"] = (a0: any) =>
		(_quote_identifier = Module["_quote_identifier"] =
			wasmExports["quote_identifier"])(a0));
	_GetSearchPathMatcher = (Module["_GetSearchPathMatcher"] = (a0: any) =>
		(_GetSearchPathMatcher = Module["_GetSearchPathMatcher"] =
			wasmExports["GetSearchPathMatcher"])(a0));
	_SearchPathMatchesCurrentEnvironment = (Module[
		"_SearchPathMatchesCurrentEnvironment"
	] = (a0: any) =>
		(_SearchPathMatchesCurrentEnvironment = Module[
			"_SearchPathMatchesCurrentEnvironment"
		] =
			wasmExports["SearchPathMatchesCurrentEnvironment"])(a0));
	let _get_collation_oid = (Module["_get_collation_oid"] = (a0: any, a1: any) =>
		(_get_collation_oid = Module["_get_collation_oid"] =
			wasmExports["get_collation_oid"])(a0, a1));
	_CacheRegisterSyscacheCallback = (Module[
		"_CacheRegisterSyscacheCallback"
	] = (a0: any, a1: any, a2: any) =>
		(_CacheRegisterSyscacheCallback = Module["_CacheRegisterSyscacheCallback"] =
			wasmExports["CacheRegisterSyscacheCallback"])(a0, a1, a2));
	let _get_extension_oid = (Module["_get_extension_oid"] = (a0: any, a1: any) =>
		(_get_extension_oid = Module["_get_extension_oid"] =
			wasmExports["get_extension_oid"])(a0, a1));
	let _get_role_oid = (Module["_get_role_oid"] = (a0: any, a1: any) =>
		(_get_role_oid = Module["_get_role_oid"] = wasmExports["get_role_oid"])(
			a0,
			a1,
		));
	_GetForeignServerByName = (Module["_GetForeignServerByName"] = (a0: any, a1: any) =>
		(_GetForeignServerByName = Module["_GetForeignServerByName"] =
			wasmExports["GetForeignServerByName"])(a0, a1));
	_typeStringToTypeName = (Module["_typeStringToTypeName"] = (a0: any, a1: any) =>
		(_typeStringToTypeName = Module["_typeStringToTypeName"] =
			wasmExports["typeStringToTypeName"])(a0, a1));
	let _list_make2_impl = (Module["_list_make2_impl"] = (a0: any, a1: any, a2: any) =>
		(_list_make2_impl = Module["_list_make2_impl"] =
			wasmExports["list_make2_impl"])(a0, a1, a2));
	_GetUserNameFromId = (Module["_GetUserNameFromId"] = (a0: any, a1: any) =>
		(_GetUserNameFromId = Module["_GetUserNameFromId"] =
			wasmExports["GetUserNameFromId"])(a0, a1));
	let _format_type_extended = (Module["_format_type_extended"] = (a0: any, a1: any, a2: any) =>
		(_format_type_extended = Module["_format_type_extended"] =
			wasmExports["format_type_extended"])(a0, a1, a2));
	let _quote_qualified_identifier = (Module["_quote_qualified_identifier"] = (
		a0,
		a1,
	) =>
		(_quote_qualified_identifier = Module["_quote_qualified_identifier"] =
			wasmExports["quote_qualified_identifier"])(a0, a1));
	let _get_tablespace_name = (Module["_get_tablespace_name"] = (a0: any) =>
		(_get_tablespace_name = Module["_get_tablespace_name"] =
			wasmExports["get_tablespace_name"])(a0));
	_GetForeignServerExtended = (Module["_GetForeignServerExtended"] = (
		a0,
		a1,
	) =>
		(_GetForeignServerExtended = Module["_GetForeignServerExtended"] =
			wasmExports["GetForeignServerExtended"])(a0, a1));
	_GetForeignServer = (Module["_GetForeignServer"] = (a0: any) =>
		(_GetForeignServer = Module["_GetForeignServer"] =
			wasmExports["GetForeignServer"])(a0));
	let _construct_empty_array = (Module["_construct_empty_array"] = (a0: any) =>
		(_construct_empty_array = Module["_construct_empty_array"] =
			wasmExports["construct_empty_array"])(a0));
	let _format_type_be_qualified = (Module["_format_type_be_qualified"] = (a0: any) =>
		(_format_type_be_qualified = Module["_format_type_be_qualified"] =
			wasmExports["format_type_be_qualified"])(a0));
	let _get_namespace_name_or_temp = (Module["_get_namespace_name_or_temp"] = (
		a0,
	) =>
		(_get_namespace_name_or_temp = Module["_get_namespace_name_or_temp"] =
			wasmExports["get_namespace_name_or_temp"])(a0));
	let _list_make3_impl = (Module["_list_make3_impl"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_list_make3_impl = Module["_list_make3_impl"] =
			wasmExports["list_make3_impl"])(a0, a1, a2, a3));
	let _construct_md_array = (Module["_construct_md_array"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
	) =>
		(_construct_md_array = Module["_construct_md_array"] =
			wasmExports["construct_md_array"])(a0, a1, a2, a3, a4, a5, a6, a7, a8));
	let _pull_varattnos = (Module["_pull_varattnos"] = (a0: any, a1: any, a2: any) =>
		(_pull_varattnos = Module["_pull_varattnos"] =
			wasmExports["pull_varattnos"])(a0, a1, a2));
	let _get_func_name = (Module["_get_func_name"] = (a0: any) =>
		(_get_func_name = Module["_get_func_name"] = wasmExports["get_func_name"])(
			a0,
		));
	let _construct_array_builtin = (Module["_construct_array_builtin"] = (
		a0,
		a1,
		a2,
	) =>
		(_construct_array_builtin = Module["_construct_array_builtin"] =
			wasmExports["construct_array_builtin"])(a0, a1, a2));
	_makeObjectName = (Module["_makeObjectName"] = (a0: any, a1: any, a2: any) =>
		(_makeObjectName = Module["_makeObjectName"] =
			wasmExports["makeObjectName"])(a0, a1, a2));
	let _get_primary_key_attnos = (Module["_get_primary_key_attnos"] = (
		a0,
		a1,
		a2,
	) =>
		(_get_primary_key_attnos = Module["_get_primary_key_attnos"] =
			wasmExports["get_primary_key_attnos"])(a0, a1, a2));
	let _bms_is_subset = (Module["_bms_is_subset"] = (a0: any, a1: any) =>
		(_bms_is_subset = Module["_bms_is_subset"] = wasmExports["bms_is_subset"])(
			a0,
			a1,
		));
	_getExtensionOfObject = (Module["_getExtensionOfObject"] = (a0: any, a1: any) =>
		(_getExtensionOfObject = Module["_getExtensionOfObject"] =
			wasmExports["getExtensionOfObject"])(a0, a1));
	let _find_inheritance_children = (Module["_find_inheritance_children"] = (
		a0,
		a1,
	) =>
		(_find_inheritance_children = Module["_find_inheritance_children"] =
			wasmExports["find_inheritance_children"])(a0, a1));
	let _lappend_int = (Module["_lappend_int"] = (a0: any, a1: any) =>
		(_lappend_int = Module["_lappend_int"] = wasmExports["lappend_int"])(
			a0,
			a1,
		));
	let _has_superclass = (Module["_has_superclass"] = (a0: any) =>
		(_has_superclass = Module["_has_superclass"] =
			wasmExports["has_superclass"])(a0));
	_CheckFunctionValidatorAccess = (Module["_CheckFunctionValidatorAccess"] =
		(a0: any, a1: any) =>
			(_CheckFunctionValidatorAccess = Module["_CheckFunctionValidatorAccess"] =
				wasmExports["CheckFunctionValidatorAccess"])(a0, a1));
	_AcquireRewriteLocks = (Module["_AcquireRewriteLocks"] = (a0: any, a1: any, a2: any) =>
		(_AcquireRewriteLocks = Module["_AcquireRewriteLocks"] =
			wasmExports["AcquireRewriteLocks"])(a0, a1, a2));
	let _function_parse_error_transpose = (Module[
		"_function_parse_error_transpose"
	] = (a0: any) =>
		(_function_parse_error_transpose = Module[
			"_function_parse_error_transpose"
		] =
			wasmExports["function_parse_error_transpose"])(a0));
	_geterrposition = (Module["_geterrposition"] = () =>
		(_geterrposition = Module["_geterrposition"] =
			wasmExports["geterrposition"])());
	_getinternalerrposition = (Module["_getinternalerrposition"] = () =>
		(_getinternalerrposition = Module["_getinternalerrposition"] =
			wasmExports["getinternalerrposition"])());
	let _pg_mblen = (Module["_pg_mblen"] = (a0: any) =>
		(_pg_mblen = Module["_pg_mblen"] = wasmExports["pg_mblen"])(a0));
	let _pg_mbstrlen_with_len = (Module["_pg_mbstrlen_with_len"] = (a0: any, a1: any) =>
		(_pg_mbstrlen_with_len = Module["_pg_mbstrlen_with_len"] =
			wasmExports["pg_mbstrlen_with_len"])(a0, a1));
	_errposition = (Module["_errposition"] = (a0: any) =>
		(_errposition = Module["_errposition"] = wasmExports["errposition"])(a0));
	_internalerrposition = (Module["_internalerrposition"] = (a0: any) =>
		(_internalerrposition = Module["_internalerrposition"] =
			wasmExports["internalerrposition"])(a0));
	_internalerrquery = (Module["_internalerrquery"] = (a0: any) =>
		(_internalerrquery = Module["_internalerrquery"] =
			wasmExports["internalerrquery"])(a0));
	let _list_delete_nth_cell = (Module["_list_delete_nth_cell"] = (a0: any, a1: any) =>
		(_list_delete_nth_cell = Module["_list_delete_nth_cell"] =
			wasmExports["list_delete_nth_cell"])(a0, a1));
	let _get_array_type = (Module["_get_array_type"] = (a0: any) =>
		(_get_array_type = Module["_get_array_type"] =
			wasmExports["get_array_type"])(a0));
	_smgrtruncate2 = (Module["_smgrtruncate2"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_smgrtruncate2 = Module["_smgrtruncate2"] = wasmExports["smgrtruncate2"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_smgrreadv = (Module["_smgrreadv"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_smgrreadv = Module["_smgrreadv"] = wasmExports["smgrreadv"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_NewRelationCreateToastTable = (Module["_NewRelationCreateToastTable"] = (
		a0,
		a1,
	) =>
		(_NewRelationCreateToastTable = Module["_NewRelationCreateToastTable"] =
			wasmExports["NewRelationCreateToastTable"])(a0, a1));
	_transformStmt = (Module["_transformStmt"] = (a0: any, a1: any) =>
		(_transformStmt = Module["_transformStmt"] = wasmExports["transformStmt"])(
			a0,
			a1,
		));
	_exprLocation = (Module["_exprLocation"] = (a0: any) =>
		(_exprLocation = Module["_exprLocation"] = wasmExports["exprLocation"])(
			a0,
		));
	_ParseFuncOrColumn = (Module["_ParseFuncOrColumn"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_ParseFuncOrColumn = Module["_ParseFuncOrColumn"] =
			wasmExports["ParseFuncOrColumn"])(a0, a1, a2, a3, a4, a5, a6));
	_exprCollation = (Module["_exprCollation"] = (a0: any) =>
		(_exprCollation = Module["_exprCollation"] = wasmExports["exprCollation"])(
			a0,
		));
	_transformDistinctClause = (Module["_transformDistinctClause"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_transformDistinctClause = Module["_transformDistinctClause"] =
			wasmExports["transformDistinctClause"])(a0, a1, a2, a3));
	_makeTargetEntry = (Module["_makeTargetEntry"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_makeTargetEntry = Module["_makeTargetEntry"] =
			wasmExports["makeTargetEntry"])(a0, a1, a2, a3));
	_makeAlias = (Module["_makeAlias"] = (a0: any, a1: any) =>
		(_makeAlias = Module["_makeAlias"] = wasmExports["makeAlias"])(a0, a1));
	_addRangeTableEntryForSubquery = (Module[
		"_addRangeTableEntryForSubquery"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_addRangeTableEntryForSubquery = Module["_addRangeTableEntryForSubquery"] =
			wasmExports["addRangeTableEntryForSubquery"])(a0, a1, a2, a3, a4));
	_makeVar = (Module["_makeVar"] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_makeVar = Module["_makeVar"] = wasmExports["makeVar"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
		));
	_makeBoolean = (Module["_makeBoolean"] = (a0: any) =>
		(_makeBoolean = Module["_makeBoolean"] = wasmExports["makeBoolean"])(a0));
	_makeInteger = (Module["_makeInteger"] = (a0: any) =>
		(_makeInteger = Module["_makeInteger"] = wasmExports["makeInteger"])(a0));
	_makeTypeName = (Module["_makeTypeName"] = (a0: any) =>
		(_makeTypeName = Module["_makeTypeName"] = wasmExports["makeTypeName"])(
			a0,
		));
	_makeFuncCall = (Module["_makeFuncCall"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_makeFuncCall = Module["_makeFuncCall"] = wasmExports["makeFuncCall"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _list_make4_impl = (Module["_list_make4_impl"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_list_make4_impl = Module["_list_make4_impl"] =
			wasmExports["list_make4_impl"])(a0, a1, a2, a3, a4));
	let _get_sortgroupclause_tle = (Module["_get_sortgroupclause_tle"] = (
		a0,
		a1,
	) =>
		(_get_sortgroupclause_tle = Module["_get_sortgroupclause_tle"] =
			wasmExports["get_sortgroupclause_tle"])(a0, a1));
	let _flatten_join_alias_vars = (Module["_flatten_join_alias_vars"] = (
		a0,
		a1,
		a2,
	) =>
		(_flatten_join_alias_vars = Module["_flatten_join_alias_vars"] =
			wasmExports["flatten_join_alias_vars"])(a0, a1, a2));
	let _list_member_int = (Module["_list_member_int"] = (a0: any, a1: any) =>
		(_list_member_int = Module["_list_member_int"] =
			wasmExports["list_member_int"])(a0, a1));
	_addRangeTableEntryForENR = (Module["_addRangeTableEntryForENR"] = (
		a0,
		a1,
		a2,
	) =>
		(_addRangeTableEntryForENR = Module["_addRangeTableEntryForENR"] =
			wasmExports["addRangeTableEntryForENR"])(a0, a1, a2));
	_typenameTypeIdAndMod = (Module["_typenameTypeIdAndMod"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_typenameTypeIdAndMod = Module["_typenameTypeIdAndMod"] =
			wasmExports["typenameTypeIdAndMod"])(a0, a1, a2, a3));
	let _get_typcollation = (Module["_get_typcollation"] = (a0: any) =>
		(_get_typcollation = Module["_get_typcollation"] =
			wasmExports["get_typcollation"])(a0));
	let _strip_implicit_coercions = (Module["_strip_implicit_coercions"] = (a0: any) =>
		(_strip_implicit_coercions = Module["_strip_implicit_coercions"] =
			wasmExports["strip_implicit_coercions"])(a0));
	let _get_sortgroupref_tle = (Module["_get_sortgroupref_tle"] = (a0: any, a1: any) =>
		(_get_sortgroupref_tle = Module["_get_sortgroupref_tle"] =
			wasmExports["get_sortgroupref_tle"])(a0, a1));
	let _contain_aggs_of_level = (Module["_contain_aggs_of_level"] = (a0: any, a1: any) =>
		(_contain_aggs_of_level = Module["_contain_aggs_of_level"] =
			wasmExports["contain_aggs_of_level"])(a0, a1));
	_typeidType = (Module["_typeidType"] = (a0: any) =>
		(_typeidType = Module["_typeidType"] = wasmExports["typeidType"])(a0));
	_typeTypeCollation = (Module["_typeTypeCollation"] = (a0: any) =>
		(_typeTypeCollation = Module["_typeTypeCollation"] =
			wasmExports["typeTypeCollation"])(a0));
	_typeLen = (Module["_typeLen"] = (a0: any) =>
		(_typeLen = Module["_typeLen"] = wasmExports["typeLen"])(a0));
	_typeByVal = (Module["_typeByVal"] = (a0: any) =>
		(_typeByVal = Module["_typeByVal"] = wasmExports["typeByVal"])(a0));
	_makeConst = (Module["_makeConst"] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) =>
		(_makeConst = Module["_makeConst"] = wasmExports["makeConst"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
		));
	let _lookup_rowtype_tupdesc = (Module["_lookup_rowtype_tupdesc"] = (a0: any, a1: any) =>
		(_lookup_rowtype_tupdesc = Module["_lookup_rowtype_tupdesc"] =
			wasmExports["lookup_rowtype_tupdesc"])(a0, a1));
	let _bms_del_member = (Module["_bms_del_member"] = (a0: any, a1: any) =>
		(_bms_del_member = Module["_bms_del_member"] =
			wasmExports["bms_del_member"])(a0, a1));
	let _list_member = (Module["_list_member"] = (a0: any, a1: any) =>
		(_list_member = Module["_list_member"] = wasmExports["list_member"])(
			a0,
			a1,
		));
	let _type_is_rowtype = (Module["_type_is_rowtype"] = (a0: any) =>
		(_type_is_rowtype = Module["_type_is_rowtype"] =
			wasmExports["type_is_rowtype"])(a0));
	let _bit_in = (Module["_bit_in"] = (a0: any) =>
		(_bit_in = Module["_bit_in"] = wasmExports["bit_in"])(a0));
	let _bms_union = (Module["_bms_union"] = (a0: any, a1: any) =>
		(_bms_union = Module["_bms_union"] = wasmExports["bms_union"])(a0, a1));
	let _varstr_levenshtein_less_equal = (Module[
		"_varstr_levenshtein_less_equal"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any) =>
		(_varstr_levenshtein_less_equal = Module["_varstr_levenshtein_less_equal"] =
			wasmExports["varstr_levenshtein_less_equal"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
		));
	let _errsave_start = (Module["_errsave_start"] = (a0: any, a1: any) =>
		(_errsave_start = Module["_errsave_start"] = wasmExports["errsave_start"])(
			a0,
			a1,
		));
	let _errsave_finish = (Module["_errsave_finish"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_errsave_finish = Module["_errsave_finish"] =
			wasmExports["errsave_finish"])(a0, a1, a2, a3));
	_makeColumnDef = (Module["_makeColumnDef"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_makeColumnDef = Module["_makeColumnDef"] = wasmExports["makeColumnDef"])(
			a0,
			a1,
			a2,
			a3,
		));
	_GetDefaultOpClass = (Module["_GetDefaultOpClass"] = (a0: any, a1: any) =>
		(_GetDefaultOpClass = Module["_GetDefaultOpClass"] =
			wasmExports["GetDefaultOpClass"])(a0, a1));
	let _scanner_init = (Module["_scanner_init"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_scanner_init = Module["_scanner_init"] = wasmExports["scanner_init"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _scanner_finish = (Module["_scanner_finish"] = (a0: any) =>
		(_scanner_finish = Module["_scanner_finish"] =
			wasmExports["scanner_finish"])(a0));
	let _core_yylex = (Module["_core_yylex"] = (a0: any, a1: any, a2: any) =>
		(_core_yylex = Module["_core_yylex"] = wasmExports["core_yylex"])(
			a0,
			a1,
			a2,
		));
	_isxdigit = (Module["_isxdigit"] = (a0: any) =>
		(_isxdigit = Module["_isxdigit"] = wasmExports["isxdigit"])(a0));
	let _scanner_isspace = (Module["_scanner_isspace"] = (a0: any) =>
		(_scanner_isspace = Module["_scanner_isspace"] =
			wasmExports["scanner_isspace"])(a0));
	let _truncate_identifier = (Module["_truncate_identifier"] = (a0: any, a1: any, a2: any) =>
		(_truncate_identifier = Module["_truncate_identifier"] =
			wasmExports["truncate_identifier"])(a0, a1, a2));
	let _downcase_truncate_identifier = (Module["_downcase_truncate_identifier"] =
		(a0: any, a1: any, a2: any) =>
			(_downcase_truncate_identifier = Module["_downcase_truncate_identifier"] =
				wasmExports["downcase_truncate_identifier"])(a0, a1, a2));
	let _pg_database_encoding_max_length = (Module[
		"_pg_database_encoding_max_length"
	] = () =>
		(_pg_database_encoding_max_length = Module[
			"_pg_database_encoding_max_length"
		] =
			wasmExports["pg_database_encoding_max_length"])());
	_namein = (Module["_namein"] = (a0: any) =>
		(_namein = Module["_namein"] = wasmExports["namein"])(a0));
	let _BlockSampler_Init = (Module["_BlockSampler_Init"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_BlockSampler_Init = Module["_BlockSampler_Init"] =
			wasmExports["BlockSampler_Init"])(a0, a1, a2, a3));
	let _reservoir_init_selection_state = (Module[
		"_reservoir_init_selection_state"
	] = (a0: any, a1: any) =>
		(_reservoir_init_selection_state = Module[
			"_reservoir_init_selection_state"
		] =
			wasmExports["reservoir_init_selection_state"])(a0, a1));
	let _reservoir_get_next_S = (Module["_reservoir_get_next_S"] = (a0: any, a1: any, a2: any) =>
		(_reservoir_get_next_S = Module["_reservoir_get_next_S"] =
			wasmExports["reservoir_get_next_S"])(a0, a1, a2));
	let _sampler_random_fract = (Module["_sampler_random_fract"] = (a0: any) =>
		(_sampler_random_fract = Module["_sampler_random_fract"] =
			wasmExports["sampler_random_fract"])(a0));
	let _BlockSampler_HasMore = (Module["_BlockSampler_HasMore"] = (a0: any) =>
		(_BlockSampler_HasMore = Module["_BlockSampler_HasMore"] =
			wasmExports["BlockSampler_HasMore"])(a0));
	let _BlockSampler_Next = (Module["_BlockSampler_Next"] = (a0: any) =>
		(_BlockSampler_Next = Module["_BlockSampler_Next"] =
			wasmExports["BlockSampler_Next"])(a0));
	let _Async_Notify = (Module["_Async_Notify"] = (a0: any, a1: any) =>
		(_Async_Notify = Module["_Async_Notify"] = wasmExports["Async_Notify"])(
			a0,
			a1,
		));
	_RangeVarCallbackMaintainsTable = (Module[
		"_RangeVarCallbackMaintainsTable"
	] = (a0: any, a1: any, a2: any, a3: any) =>
		(_RangeVarCallbackMaintainsTable = Module[
			"_RangeVarCallbackMaintainsTable"
		] =
			wasmExports["RangeVarCallbackMaintainsTable"])(a0, a1, a2, a3));
	let _make_new_heap = (Module["_make_new_heap"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_make_new_heap = Module["_make_new_heap"] = wasmExports["make_new_heap"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _finish_heap_swap = (Module["_finish_heap_swap"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
	) =>
		(_finish_heap_swap = Module["_finish_heap_swap"] =
			wasmExports["finish_heap_swap"])(a0, a1, a2, a3, a4, a5, a6, a7, a8));
	let _wasm_OpenPipeStream = (Module["_wasm_OpenPipeStream"] = (a0: any, a1: any) =>
		(_wasm_OpenPipeStream = Module["_wasm_OpenPipeStream"] =
			wasmExports["wasm_OpenPipeStream"])(a0, a1));
	_ClosePipeStream = (Module["_ClosePipeStream"] = (a0: any) =>
		(_ClosePipeStream = Module["_ClosePipeStream"] =
			wasmExports["ClosePipeStream"])(a0));
	_BeginCopyFrom = (Module["_BeginCopyFrom"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_BeginCopyFrom = Module["_BeginCopyFrom"] = wasmExports["BeginCopyFrom"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
		));
	_EndCopyFrom = (Module["_EndCopyFrom"] = (a0: any) =>
		(_EndCopyFrom = Module["_EndCopyFrom"] = wasmExports["EndCopyFrom"])(a0));
	_ProcessCopyOptions = (Module["_ProcessCopyOptions"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_ProcessCopyOptions = Module["_ProcessCopyOptions"] =
			wasmExports["ProcessCopyOptions"])(a0, a1, a2, a3));
	_CopyFromErrorCallback = (Module["_CopyFromErrorCallback"] = (a0: any) =>
		(_CopyFromErrorCallback = Module["_CopyFromErrorCallback"] =
			wasmExports["CopyFromErrorCallback"])(a0));
	_NextCopyFrom = (Module["_NextCopyFrom"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_NextCopyFrom = Module["_NextCopyFrom"] = wasmExports["NextCopyFrom"])(
			a0,
			a1,
			a2,
			a3,
		));
	_ExecInitExpr = (Module["_ExecInitExpr"] = (a0: any, a1: any) =>
		(_ExecInitExpr = Module["_ExecInitExpr"] = wasmExports["ExecInitExpr"])(
			a0,
			a1,
		));
	_tolower = (Module["_tolower"] = (a0: any) =>
		(_tolower = Module["_tolower"] = wasmExports["tolower"])(a0));
	_PushCopiedSnapshot = (Module["_PushCopiedSnapshot"] = (a0: any) =>
		(_PushCopiedSnapshot = Module["_PushCopiedSnapshot"] =
			wasmExports["PushCopiedSnapshot"])(a0));
	_UpdateActiveSnapshotCommandId = (Module[
		"_UpdateActiveSnapshotCommandId"
	] = () =>
		(_UpdateActiveSnapshotCommandId = Module["_UpdateActiveSnapshotCommandId"] =
			wasmExports["UpdateActiveSnapshotCommandId"])());
	_CreateQueryDesc = (Module["_CreateQueryDesc"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_CreateQueryDesc = Module["_CreateQueryDesc"] =
			wasmExports["CreateQueryDesc"])(a0, a1, a2, a3, a4, a5, a6, a7));
	_ExecutorStart = (Module["_ExecutorStart"] = (a0: any, a1: any) =>
		(_ExecutorStart = Module["_ExecutorStart"] = wasmExports["ExecutorStart"])(
			a0,
			a1,
		));
	_ExecutorFinish = (Module["_ExecutorFinish"] = (a0: any) =>
		(_ExecutorFinish = Module["_ExecutorFinish"] =
			wasmExports["ExecutorFinish"])(a0));
	_ExecutorEnd = (Module["_ExecutorEnd"] = (a0: any) =>
		(_ExecutorEnd = Module["_ExecutorEnd"] = wasmExports["ExecutorEnd"])(a0));
	_FreeQueryDesc = (Module["_FreeQueryDesc"] = (a0: any) =>
		(_FreeQueryDesc = Module["_FreeQueryDesc"] = wasmExports["FreeQueryDesc"])(
			a0,
		));
	let _pg_server_to_any = (Module["_pg_server_to_any"] = (a0: any, a1: any, a2: any) =>
		(_pg_server_to_any = Module["_pg_server_to_any"] =
			wasmExports["pg_server_to_any"])(a0, a1, a2));
	_ExecutorRun = (Module["_ExecutorRun"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_ExecutorRun = Module["_ExecutorRun"] = wasmExports["ExecutorRun"])(
			a0,
			a1,
			a2,
			a3,
		));
	_CreateTableAsRelExists = (Module["_CreateTableAsRelExists"] = (a0: any) =>
		(_CreateTableAsRelExists = Module["_CreateTableAsRelExists"] =
			wasmExports["CreateTableAsRelExists"])(a0));
	_DefineRelation = (Module["_DefineRelation"] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_DefineRelation = Module["_DefineRelation"] =
			wasmExports["DefineRelation"])(a0, a1, a2, a3, a4, a5));
	_oidin = (Module["_oidin"] = (a0: any) =>
		(_oidin = Module["_oidin"] = wasmExports["oidin"])(a0));
	_GetCommandTagName = (Module["_GetCommandTagName"] = (a0: any) =>
		(_GetCommandTagName = Module["_GetCommandTagName"] =
			wasmExports["GetCommandTagName"])(a0));
	_ExplainBeginOutput = (Module["_ExplainBeginOutput"] = (a0: any) =>
		(_ExplainBeginOutput = Module["_ExplainBeginOutput"] =
			wasmExports["ExplainBeginOutput"])(a0));
	_NewExplainState = (Module["_NewExplainState"] = () =>
		(_NewExplainState = Module["_NewExplainState"] =
			wasmExports["NewExplainState"])());
	_ExplainEndOutput = (Module["_ExplainEndOutput"] = (a0: any) =>
		(_ExplainEndOutput = Module["_ExplainEndOutput"] =
			wasmExports["ExplainEndOutput"])(a0));
	_ExplainPrintPlan = (Module["_ExplainPrintPlan"] = (a0: any, a1: any) =>
		(_ExplainPrintPlan = Module["_ExplainPrintPlan"] =
			wasmExports["ExplainPrintPlan"])(a0, a1));
	_ExplainPrintTriggers = (Module["_ExplainPrintTriggers"] = (a0: any, a1: any) =>
		(_ExplainPrintTriggers = Module["_ExplainPrintTriggers"] =
			wasmExports["ExplainPrintTriggers"])(a0, a1));
	_ExplainPrintJITSummary = (Module["_ExplainPrintJITSummary"] = (a0: any, a1: any) =>
		(_ExplainPrintJITSummary = Module["_ExplainPrintJITSummary"] =
			wasmExports["ExplainPrintJITSummary"])(a0, a1));
	_InstrEndLoop = (Module["_InstrEndLoop"] = (a0: any) =>
		(_InstrEndLoop = Module["_InstrEndLoop"] = wasmExports["InstrEndLoop"])(
			a0,
		));
	_ExplainPropertyInteger = (Module["_ExplainPropertyInteger"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_ExplainPropertyInteger = Module["_ExplainPropertyInteger"] =
			wasmExports["ExplainPropertyInteger"])(a0, a1, a2, a3));
	_ExplainQueryText = (Module["_ExplainQueryText"] = (a0: any, a1: any) =>
		(_ExplainQueryText = Module["_ExplainQueryText"] =
			wasmExports["ExplainQueryText"])(a0, a1));
	_ExplainPropertyText = (Module["_ExplainPropertyText"] = (a0: any, a1: any, a2: any) =>
		(_ExplainPropertyText = Module["_ExplainPropertyText"] =
			wasmExports["ExplainPropertyText"])(a0, a1, a2));
	_ExplainQueryParameters = (Module["_ExplainQueryParameters"] = (
		a0,
		a1,
		a2,
	) =>
		(_ExplainQueryParameters = Module["_ExplainQueryParameters"] =
			wasmExports["ExplainQueryParameters"])(a0, a1, a2));
	let _get_func_namespace = (Module["_get_func_namespace"] = (a0: any) =>
		(_get_func_namespace = Module["_get_func_namespace"] =
			wasmExports["get_func_namespace"])(a0));
	let _get_rel_type_id = (Module["_get_rel_type_id"] = (a0: any) =>
		(_get_rel_type_id = Module["_get_rel_type_id"] =
			wasmExports["get_rel_type_id"])(a0));
	let _set_config_option = (Module["_set_config_option"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_set_config_option = Module["_set_config_option"] =
			wasmExports["set_config_option"])(a0, a1, a2, a3, a4, a5, a6, a7));
	let _pg_any_to_server = (Module["_pg_any_to_server"] = (a0: any, a1: any, a2: any) =>
		(_pg_any_to_server = Module["_pg_any_to_server"] =
			wasmExports["pg_any_to_server"])(a0, a1, a2));
	_DirectFunctionCall4Coll = (Module["_DirectFunctionCall4Coll"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_DirectFunctionCall4Coll = Module["_DirectFunctionCall4Coll"] =
			wasmExports["DirectFunctionCall4Coll"])(a0, a1, a2, a3, a4, a5));
	let _list_delete_cell = (Module["_list_delete_cell"] = (a0: any, a1: any) =>
		(_list_delete_cell = Module["_list_delete_cell"] =
			wasmExports["list_delete_cell"])(a0, a1));
	_GetForeignDataWrapper = (Module["_GetForeignDataWrapper"] = (a0: any) =>
		(_GetForeignDataWrapper = Module["_GetForeignDataWrapper"] =
			wasmExports["GetForeignDataWrapper"])(a0));
	_CreateExprContext = (Module["_CreateExprContext"] = (a0: any) =>
		(_CreateExprContext = Module["_CreateExprContext"] =
			wasmExports["CreateExprContext"])(a0));
	_EnsurePortalSnapshotExists = (Module["_EnsurePortalSnapshotExists"] =
		() =>
			(_EnsurePortalSnapshotExists = Module["_EnsurePortalSnapshotExists"] =
				wasmExports["EnsurePortalSnapshotExists"])());
	_CheckIndexCompatible = (Module["_CheckIndexCompatible"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_CheckIndexCompatible = Module["_CheckIndexCompatible"] =
			wasmExports["CheckIndexCompatible"])(a0, a1, a2, a3));
	let _pgstat_count_truncate = (Module["_pgstat_count_truncate"] = (a0: any) =>
		(_pgstat_count_truncate = Module["_pgstat_count_truncate"] =
			wasmExports["pgstat_count_truncate"])(a0));
	let _SPI_connect = (Module["_SPI_connect"] = () =>
		(_SPI_connect = Module["_SPI_connect"] = wasmExports["SPI_connect"])());
	let _SPI_exec = (Module["_SPI_exec"] = (a0: any, a1: any) =>
		(_SPI_exec = Module["_SPI_exec"] = wasmExports["SPI_exec"])(a0, a1));
	let _SPI_execute = (Module["_SPI_execute"] = (a0: any, a1: any, a2: any) =>
		(_SPI_execute = Module["_SPI_execute"] = wasmExports["SPI_execute"])(
			a0,
			a1,
			a2,
		));
	let _SPI_getvalue = (Module["_SPI_getvalue"] = (a0: any, a1: any, a2: any) =>
		(_SPI_getvalue = Module["_SPI_getvalue"] = wasmExports["SPI_getvalue"])(
			a0,
			a1,
			a2,
		));
	let _generate_operator_clause = (Module["_generate_operator_clause"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_generate_operator_clause = Module["_generate_operator_clause"] =
			wasmExports["generate_operator_clause"])(a0, a1, a2, a3, a4, a5));
	let _SPI_finish = (Module["_SPI_finish"] = () =>
		(_SPI_finish = Module["_SPI_finish"] = wasmExports["SPI_finish"])());
	_CreateTransientRelDestReceiver = (Module[
		"_CreateTransientRelDestReceiver"
	] = (a0: any) =>
		(_CreateTransientRelDestReceiver = Module[
			"_CreateTransientRelDestReceiver"
		] =
			wasmExports["CreateTransientRelDestReceiver"])(a0));
	_MemoryContextSetIdentifier = (Module["_MemoryContextSetIdentifier"] = (
		a0,
		a1,
	) =>
		(_MemoryContextSetIdentifier = Module["_MemoryContextSetIdentifier"] =
			wasmExports["MemoryContextSetIdentifier"])(a0, a1));
	_checkExprHasSubLink = (Module["_checkExprHasSubLink"] = (a0: any) =>
		(_checkExprHasSubLink = Module["_checkExprHasSubLink"] =
			wasmExports["checkExprHasSubLink"])(a0));
	_SetTuplestoreDestReceiverParams = (Module[
		"_SetTuplestoreDestReceiverParams"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_SetTuplestoreDestReceiverParams = Module[
			"_SetTuplestoreDestReceiverParams"
		] =
			wasmExports["SetTuplestoreDestReceiverParams"])(a0, a1, a2, a3, a4, a5));
	let _tuplestore_rescan = (Module["_tuplestore_rescan"] = (a0: any) =>
		(_tuplestore_rescan = Module["_tuplestore_rescan"] =
			wasmExports["tuplestore_rescan"])(a0));
	_MemoryContextDeleteChildren = (Module["_MemoryContextDeleteChildren"] = (
		a0,
	) =>
		(_MemoryContextDeleteChildren = Module["_MemoryContextDeleteChildren"] =
			wasmExports["MemoryContextDeleteChildren"])(a0));
	_ReleaseCachedPlan = (Module["_ReleaseCachedPlan"] = (a0: any, a1: any) =>
		(_ReleaseCachedPlan = Module["_ReleaseCachedPlan"] =
			wasmExports["ReleaseCachedPlan"])(a0, a1));
	_nextval = (Module["_nextval"] = (a0: any) =>
		(_nextval = Module["_nextval"] = wasmExports["nextval"])(a0));
	_textToQualifiedNameList = (Module["_textToQualifiedNameList"] = (a0: any) =>
		(_textToQualifiedNameList = Module["_textToQualifiedNameList"] =
			wasmExports["textToQualifiedNameList"])(a0));
	let _tuplestore_gettupleslot = (Module["_tuplestore_gettupleslot"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_tuplestore_gettupleslot = Module["_tuplestore_gettupleslot"] =
			wasmExports["tuplestore_gettupleslot"])(a0, a1, a2, a3));
	let _list_delete = (Module["_list_delete"] = (a0: any, a1: any) =>
		(_list_delete = Module["_list_delete"] = wasmExports["list_delete"])(
			a0,
			a1,
		));
	let _tuplestore_end = (Module["_tuplestore_end"] = (a0: any) =>
		(_tuplestore_end = Module["_tuplestore_end"] =
			wasmExports["tuplestore_end"])(a0));
	let _quote_literal_cstr = (Module["_quote_literal_cstr"] = (a0: any) =>
		(_quote_literal_cstr = Module["_quote_literal_cstr"] =
			wasmExports["quote_literal_cstr"])(a0));
	let _contain_mutable_functions = (Module["_contain_mutable_functions"] = (
		a0,
	) =>
		(_contain_mutable_functions = Module["_contain_mutable_functions"] =
			wasmExports["contain_mutable_functions"])(a0));
	_ExecuteTruncateGuts = (Module["_ExecuteTruncateGuts"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_ExecuteTruncateGuts = Module["_ExecuteTruncateGuts"] =
			wasmExports["ExecuteTruncateGuts"])(a0, a1, a2, a3, a4, a5));
	let _bms_make_singleton = (Module["_bms_make_singleton"] = (a0: any) =>
		(_bms_make_singleton = Module["_bms_make_singleton"] =
			wasmExports["bms_make_singleton"])(a0));
	let _tuplestore_puttupleslot = (Module["_tuplestore_puttupleslot"] = (
		a0,
		a1,
	) =>
		(_tuplestore_puttupleslot = Module["_tuplestore_puttupleslot"] =
			wasmExports["tuplestore_puttupleslot"])(a0, a1));
	let _tuplestore_begin_heap = (Module["_tuplestore_begin_heap"] = (
		a0,
		a1,
		a2,
	) =>
		(_tuplestore_begin_heap = Module["_tuplestore_begin_heap"] =
			wasmExports["tuplestore_begin_heap"])(a0, a1, a2));
	_ExecForceStoreHeapTuple = (Module["_ExecForceStoreHeapTuple"] = (
		a0,
		a1,
		a2,
	) =>
		(_ExecForceStoreHeapTuple = Module["_ExecForceStoreHeapTuple"] =
			wasmExports["ExecForceStoreHeapTuple"])(a0, a1, a2));
	_strtod = (Module["_strtod"] = (a0: any, a1: any) =>
		(_strtod = Module["_strtod"] = wasmExports["strtod"])(a0, a1));
	let _plain_crypt_verify = (Module["_plain_crypt_verify"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_plain_crypt_verify = Module["_plain_crypt_verify"] =
			wasmExports["plain_crypt_verify"])(a0, a1, a2, a3));
	_ProcessConfigFile = (Module["_ProcessConfigFile"] = (a0: any) =>
		(_ProcessConfigFile = Module["_ProcessConfigFile"] =
			wasmExports["ProcessConfigFile"])(a0));
	_ExecReScan = (Module["_ExecReScan"] = (a0: any) =>
		(_ExecReScan = Module["_ExecReScan"] = wasmExports["ExecReScan"])(a0));
	_ExecAsyncResponse = (Module["_ExecAsyncResponse"] = (a0: any) =>
		(_ExecAsyncResponse = Module["_ExecAsyncResponse"] =
			wasmExports["ExecAsyncResponse"])(a0));
	_ExecAsyncRequestDone = (Module["_ExecAsyncRequestDone"] = (a0: any, a1: any) =>
		(_ExecAsyncRequestDone = Module["_ExecAsyncRequestDone"] =
			wasmExports["ExecAsyncRequestDone"])(a0, a1));
	_ExecAsyncRequestPending = (Module["_ExecAsyncRequestPending"] = (a0: any) =>
		(_ExecAsyncRequestPending = Module["_ExecAsyncRequestPending"] =
			wasmExports["ExecAsyncRequestPending"])(a0));
	_ExprEvalPushStep = (Module["_ExprEvalPushStep"] = (a0: any, a1: any) =>
		(_ExprEvalPushStep = Module["_ExprEvalPushStep"] =
			wasmExports["ExprEvalPushStep"])(a0, a1));
	_ExecInitExprWithParams = (Module["_ExecInitExprWithParams"] = (a0: any, a1: any) =>
		(_ExecInitExprWithParams = Module["_ExecInitExprWithParams"] =
			wasmExports["ExecInitExprWithParams"])(a0, a1));
	_ExecInitExprList = (Module["_ExecInitExprList"] = (a0: any, a1: any) =>
		(_ExecInitExprList = Module["_ExecInitExprList"] =
			wasmExports["ExecInitExprList"])(a0, a1));
	_MakeExpandedObjectReadOnlyInternal = (Module[
		"_MakeExpandedObjectReadOnlyInternal"
	] = (a0: any) =>
		(_MakeExpandedObjectReadOnlyInternal = Module[
			"_MakeExpandedObjectReadOnlyInternal"
		] =
			wasmExports["MakeExpandedObjectReadOnlyInternal"])(a0));
	let _tuplesort_puttupleslot = (Module["_tuplesort_puttupleslot"] = (a0: any, a1: any) =>
		(_tuplesort_puttupleslot = Module["_tuplesort_puttupleslot"] =
			wasmExports["tuplesort_puttupleslot"])(a0, a1));
	_ArrayGetNItems = (Module["_ArrayGetNItems"] = (a0: any, a1: any) =>
		(_ArrayGetNItems = Module["_ArrayGetNItems"] =
			wasmExports["ArrayGetNItems"])(a0, a1));
	let _expanded_record_fetch_tupdesc = (Module[
		"_expanded_record_fetch_tupdesc"
	] = (a0: any) =>
		(_expanded_record_fetch_tupdesc = Module["_expanded_record_fetch_tupdesc"] =
			wasmExports["expanded_record_fetch_tupdesc"])(a0));
	let _expanded_record_fetch_field = (Module["_expanded_record_fetch_field"] = (
		a0,
		a1,
		a2,
	) =>
		(_expanded_record_fetch_field = Module["_expanded_record_fetch_field"] =
			wasmExports["expanded_record_fetch_field"])(a0, a1, a2));
	_JsonbValueToJsonb = (Module["_JsonbValueToJsonb"] = (a0: any) =>
		(_JsonbValueToJsonb = Module["_JsonbValueToJsonb"] =
			wasmExports["JsonbValueToJsonb"])(a0));
	_boolout = (Module["_boolout"] = (a0: any) =>
		(_boolout = Module["_boolout"] = wasmExports["boolout"])(a0));
	let _lookup_rowtype_tupdesc_domain = (Module[
		"_lookup_rowtype_tupdesc_domain"
	] = (a0: any, a1: any, a2: any) =>
		(_lookup_rowtype_tupdesc_domain = Module["_lookup_rowtype_tupdesc_domain"] =
			wasmExports["lookup_rowtype_tupdesc_domain"])(a0, a1, a2));
	_MemoryContextGetParent = (Module["_MemoryContextGetParent"] = (a0: any) =>
		(_MemoryContextGetParent = Module["_MemoryContextGetParent"] =
			wasmExports["MemoryContextGetParent"])(a0));
	_DeleteExpandedObject = (Module["_DeleteExpandedObject"] = (a0: any) =>
		(_DeleteExpandedObject = Module["_DeleteExpandedObject"] =
			wasmExports["DeleteExpandedObject"])(a0));
	_ExecFindJunkAttributeInTlist = (Module["_ExecFindJunkAttributeInTlist"] =
		(a0: any, a1: any) =>
			(_ExecFindJunkAttributeInTlist = Module["_ExecFindJunkAttributeInTlist"] =
				wasmExports["ExecFindJunkAttributeInTlist"])(a0, a1));
	let _standard_ExecutorStart = (Module["_standard_ExecutorStart"] = (a0: any, a1: any) =>
		(_standard_ExecutorStart = Module["_standard_ExecutorStart"] =
			wasmExports["standard_ExecutorStart"])(a0, a1));
	let _standard_ExecutorRun = (Module["_standard_ExecutorRun"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_standard_ExecutorRun = Module["_standard_ExecutorRun"] =
			wasmExports["standard_ExecutorRun"])(a0, a1, a2, a3));
	let _standard_ExecutorFinish = (Module["_standard_ExecutorFinish"] = (a0: any) =>
		(_standard_ExecutorFinish = Module["_standard_ExecutorFinish"] =
			wasmExports["standard_ExecutorFinish"])(a0));
	let _standard_ExecutorEnd = (Module["_standard_ExecutorEnd"] = (a0: any) =>
		(_standard_ExecutorEnd = Module["_standard_ExecutorEnd"] =
			wasmExports["standard_ExecutorEnd"])(a0));
	_InstrAlloc = (Module["_InstrAlloc"] = (a0: any, a1: any, a2: any) =>
		(_InstrAlloc = Module["_InstrAlloc"] = wasmExports["InstrAlloc"])(
			a0,
			a1,
			a2,
		));
	let _get_typlenbyval = (Module["_get_typlenbyval"] = (a0: any, a1: any, a2: any) =>
		(_get_typlenbyval = Module["_get_typlenbyval"] =
			wasmExports["get_typlenbyval"])(a0, a1, a2));
	_InputFunctionCall = (Module["_InputFunctionCall"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_InputFunctionCall = Module["_InputFunctionCall"] =
			wasmExports["InputFunctionCall"])(a0, a1, a2, a3));
	_FreeExprContext = (Module["_FreeExprContext"] = (a0: any, a1: any) =>
		(_FreeExprContext = Module["_FreeExprContext"] =
			wasmExports["FreeExprContext"])(a0, a1));
	_ExecOpenScanRelation = (Module["_ExecOpenScanRelation"] = (a0: any, a1: any, a2: any) =>
		(_ExecOpenScanRelation = Module["_ExecOpenScanRelation"] =
			wasmExports["ExecOpenScanRelation"])(a0, a1, a2));
	let _bms_intersect = (Module["_bms_intersect"] = (a0: any, a1: any) =>
		(_bms_intersect = Module["_bms_intersect"] = wasmExports["bms_intersect"])(
			a0,
			a1,
		));
	_ExecGetReturningSlot = (Module["_ExecGetReturningSlot"] = (a0: any, a1: any) =>
		(_ExecGetReturningSlot = Module["_ExecGetReturningSlot"] =
			wasmExports["ExecGetReturningSlot"])(a0, a1));
	_ExecGetResultRelCheckAsUser = (Module["_ExecGetResultRelCheckAsUser"] = (
		a0,
		a1,
	) =>
		(_ExecGetResultRelCheckAsUser = Module["_ExecGetResultRelCheckAsUser"] =
			wasmExports["ExecGetResultRelCheckAsUser"])(a0, a1));
	let _get_call_expr_argtype = (Module["_get_call_expr_argtype"] = (a0: any, a1: any) =>
		(_get_call_expr_argtype = Module["_get_call_expr_argtype"] =
			wasmExports["get_call_expr_argtype"])(a0, a1));
	let _tuplestore_clear = (Module["_tuplestore_clear"] = (a0: any) =>
		(_tuplestore_clear = Module["_tuplestore_clear"] =
			wasmExports["tuplestore_clear"])(a0));
	_InstrUpdateTupleCount = (Module["_InstrUpdateTupleCount"] = (a0: any, a1: any) =>
		(_InstrUpdateTupleCount = Module["_InstrUpdateTupleCount"] =
			wasmExports["InstrUpdateTupleCount"])(a0, a1));
	let _tuplesort_begin_heap = (Module["_tuplesort_begin_heap"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
	) =>
		(_tuplesort_begin_heap = Module["_tuplesort_begin_heap"] =
			wasmExports["tuplesort_begin_heap"])(a0, a1, a2, a3, a4, a5, a6, a7, a8));
	let _tuplesort_gettupleslot = (Module["_tuplesort_gettupleslot"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_tuplesort_gettupleslot = Module["_tuplesort_gettupleslot"] =
			wasmExports["tuplesort_gettupleslot"])(a0, a1, a2, a3, a4));
	_AddWaitEventToSet = (Module["_AddWaitEventToSet"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_AddWaitEventToSet = Module["_AddWaitEventToSet"] =
			wasmExports["AddWaitEventToSet"])(a0, a1, a2, a3, a4));
	_GetNumRegisteredWaitEvents = (Module["_GetNumRegisteredWaitEvents"] = (
		a0,
	) =>
		(_GetNumRegisteredWaitEvents = Module["_GetNumRegisteredWaitEvents"] =
			wasmExports["GetNumRegisteredWaitEvents"])(a0));
	let _get_attstatsslot = (Module["_get_attstatsslot"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_get_attstatsslot = Module["_get_attstatsslot"] =
			wasmExports["get_attstatsslot"])(a0, a1, a2, a3, a4));
	let _free_attstatsslot = (Module["_free_attstatsslot"] = (a0: any) =>
		(_free_attstatsslot = Module["_free_attstatsslot"] =
			wasmExports["free_attstatsslot"])(a0));
	let _tuplesort_reset = (Module["_tuplesort_reset"] = (a0: any) =>
		(_tuplesort_reset = Module["_tuplesort_reset"] =
			wasmExports["tuplesort_reset"])(a0));
	let _pairingheap_first = (Module["_pairingheap_first"] = (a0: any) =>
		(_pairingheap_first = Module["_pairingheap_first"] =
			wasmExports["pairingheap_first"])(a0));
	let _bms_nonempty_difference = (Module["_bms_nonempty_difference"] = (
		a0,
		a1,
	) =>
		(_bms_nonempty_difference = Module["_bms_nonempty_difference"] =
			wasmExports["bms_nonempty_difference"])(a0, a1));
	let _SPI_connect_ext = (Module["_SPI_connect_ext"] = (a0: any) =>
		(_SPI_connect_ext = Module["_SPI_connect_ext"] =
			wasmExports["SPI_connect_ext"])(a0));
	let _SPI_commit = (Module["_SPI_commit"] = () =>
		(_SPI_commit = Module["_SPI_commit"] = wasmExports["SPI_commit"])());
	_CopyErrorData = (Module["_CopyErrorData"] = () =>
		(_CopyErrorData = Module["_CopyErrorData"] =
			wasmExports["CopyErrorData"])());
	_ReThrowError = (Module["_ReThrowError"] = (a0: any) =>
		(_ReThrowError = Module["_ReThrowError"] = wasmExports["ReThrowError"])(
			a0,
		));
	let _SPI_commit_and_chain = (Module["_SPI_commit_and_chain"] = () =>
		(_SPI_commit_and_chain = Module["_SPI_commit_and_chain"] =
			wasmExports["SPI_commit_and_chain"])());
	let _SPI_rollback = (Module["_SPI_rollback"] = () =>
		(_SPI_rollback = Module["_SPI_rollback"] = wasmExports["SPI_rollback"])());
	let _SPI_rollback_and_chain = (Module["_SPI_rollback_and_chain"] = () =>
		(_SPI_rollback_and_chain = Module["_SPI_rollback_and_chain"] =
			wasmExports["SPI_rollback_and_chain"])());
	let _SPI_freetuptable = (Module["_SPI_freetuptable"] = (a0: any) =>
		(_SPI_freetuptable = Module["_SPI_freetuptable"] =
			wasmExports["SPI_freetuptable"])(a0));
	let _SPI_execute_extended = (Module["_SPI_execute_extended"] = (a0: any, a1: any) =>
		(_SPI_execute_extended = Module["_SPI_execute_extended"] =
			wasmExports["SPI_execute_extended"])(a0, a1));
	let _SPI_execute_plan = (Module["_SPI_execute_plan"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_SPI_execute_plan = Module["_SPI_execute_plan"] =
			wasmExports["SPI_execute_plan"])(a0, a1, a2, a3, a4));
	let _SPI_execp = (Module["_SPI_execp"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_SPI_execp = Module["_SPI_execp"] = wasmExports["SPI_execp"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _SPI_execute_plan_extended = (Module["_SPI_execute_plan_extended"] = (
		a0,
		a1,
	) =>
		(_SPI_execute_plan_extended = Module["_SPI_execute_plan_extended"] =
			wasmExports["SPI_execute_plan_extended"])(a0, a1));
	let _SPI_execute_plan_with_paramlist = (Module[
		"_SPI_execute_plan_with_paramlist"
	] = (a0: any, a1: any, a2: any, a3: any) =>
		(_SPI_execute_plan_with_paramlist = Module[
			"_SPI_execute_plan_with_paramlist"
		] =
			wasmExports["SPI_execute_plan_with_paramlist"])(a0, a1, a2, a3));
	let _SPI_prepare = (Module["_SPI_prepare"] = (a0: any, a1: any, a2: any) =>
		(_SPI_prepare = Module["_SPI_prepare"] = wasmExports["SPI_prepare"])(
			a0,
			a1,
			a2,
		));
	let _SPI_prepare_extended = (Module["_SPI_prepare_extended"] = (a0: any, a1: any) =>
		(_SPI_prepare_extended = Module["_SPI_prepare_extended"] =
			wasmExports["SPI_prepare_extended"])(a0, a1));
	let _SPI_keepplan = (Module["_SPI_keepplan"] = (a0: any) =>
		(_SPI_keepplan = Module["_SPI_keepplan"] = wasmExports["SPI_keepplan"])(
			a0,
		));
	let _SPI_freeplan = (Module["_SPI_freeplan"] = (a0: any) =>
		(_SPI_freeplan = Module["_SPI_freeplan"] = wasmExports["SPI_freeplan"])(
			a0,
		));
	let _SPI_copytuple = (Module["_SPI_copytuple"] = (a0: any) =>
		(_SPI_copytuple = Module["_SPI_copytuple"] = wasmExports["SPI_copytuple"])(
			a0,
		));
	let _SPI_returntuple = (Module["_SPI_returntuple"] = (a0: any, a1: any) =>
		(_SPI_returntuple = Module["_SPI_returntuple"] =
			wasmExports["SPI_returntuple"])(a0, a1));
	let _SPI_fnumber = (Module["_SPI_fnumber"] = (a0: any, a1: any) =>
		(_SPI_fnumber = Module["_SPI_fnumber"] = wasmExports["SPI_fnumber"])(
			a0,
			a1,
		));
	let _SPI_fname = (Module["_SPI_fname"] = (a0: any, a1: any) =>
		(_SPI_fname = Module["_SPI_fname"] = wasmExports["SPI_fname"])(a0, a1));
	let _SPI_getbinval = (Module["_SPI_getbinval"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_SPI_getbinval = Module["_SPI_getbinval"] = wasmExports["SPI_getbinval"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _SPI_gettype = (Module["_SPI_gettype"] = (a0: any, a1: any) =>
		(_SPI_gettype = Module["_SPI_gettype"] = wasmExports["SPI_gettype"])(
			a0,
			a1,
		));
	let _SPI_gettypeid = (Module["_SPI_gettypeid"] = (a0: any, a1: any) =>
		(_SPI_gettypeid = Module["_SPI_gettypeid"] = wasmExports["SPI_gettypeid"])(
			a0,
			a1,
		));
	let _SPI_getrelname = (Module["_SPI_getrelname"] = (a0: any) =>
		(_SPI_getrelname = Module["_SPI_getrelname"] =
			wasmExports["SPI_getrelname"])(a0));
	let _SPI_palloc = (Module["_SPI_palloc"] = (a0: any) =>
		(_SPI_palloc = Module["_SPI_palloc"] = wasmExports["SPI_palloc"])(a0));
	let _SPI_datumTransfer = (Module["_SPI_datumTransfer"] = (a0: any, a1: any, a2: any) =>
		(_SPI_datumTransfer = Module["_SPI_datumTransfer"] =
			wasmExports["SPI_datumTransfer"])(a0, a1, a2));
	_datumTransfer = (Module["_datumTransfer"] = (a0: any, a1: any, a2: any) =>
		(_datumTransfer = Module["_datumTransfer"] = wasmExports["datumTransfer"])(
			a0,
			a1,
			a2,
		));
	let _SPI_cursor_open_with_paramlist = (Module[
		"_SPI_cursor_open_with_paramlist"
	] = (a0: any, a1: any, a2: any, a3: any) =>
		(_SPI_cursor_open_with_paramlist = Module[
			"_SPI_cursor_open_with_paramlist"
		] =
			wasmExports["SPI_cursor_open_with_paramlist"])(a0, a1, a2, a3));
	let _SPI_cursor_parse_open = (Module["_SPI_cursor_parse_open"] = (
		a0,
		a1,
		a2,
	) =>
		(_SPI_cursor_parse_open = Module["_SPI_cursor_parse_open"] =
			wasmExports["SPI_cursor_parse_open"])(a0, a1, a2));
	let _SPI_cursor_find = (Module["_SPI_cursor_find"] = (a0: any) =>
		(_SPI_cursor_find = Module["_SPI_cursor_find"] =
			wasmExports["SPI_cursor_find"])(a0));
	let _SPI_cursor_fetch = (Module["_SPI_cursor_fetch"] = (a0: any, a1: any, a2: any) =>
		(_SPI_cursor_fetch = Module["_SPI_cursor_fetch"] =
			wasmExports["SPI_cursor_fetch"])(a0, a1, a2));
	let _SPI_scroll_cursor_fetch = (Module["_SPI_scroll_cursor_fetch"] = (
		a0,
		a1,
		a2,
	) =>
		(_SPI_scroll_cursor_fetch = Module["_SPI_scroll_cursor_fetch"] =
			wasmExports["SPI_scroll_cursor_fetch"])(a0, a1, a2));
	let _SPI_scroll_cursor_move = (Module["_SPI_scroll_cursor_move"] = (
		a0,
		a1,
		a2,
	) =>
		(_SPI_scroll_cursor_move = Module["_SPI_scroll_cursor_move"] =
			wasmExports["SPI_scroll_cursor_move"])(a0, a1, a2));
	let _SPI_cursor_close = (Module["_SPI_cursor_close"] = (a0: any) =>
		(_SPI_cursor_close = Module["_SPI_cursor_close"] =
			wasmExports["SPI_cursor_close"])(a0));
	let _SPI_plan_is_valid = (Module["_SPI_plan_is_valid"] = (a0: any) =>
		(_SPI_plan_is_valid = Module["_SPI_plan_is_valid"] =
			wasmExports["SPI_plan_is_valid"])(a0));
	let _SPI_result_code_string = (Module["_SPI_result_code_string"] = (a0: any) =>
		(_SPI_result_code_string = Module["_SPI_result_code_string"] =
			wasmExports["SPI_result_code_string"])(a0));
	let _SPI_plan_get_plan_sources = (Module["_SPI_plan_get_plan_sources"] = (
		a0,
	) =>
		(_SPI_plan_get_plan_sources = Module["_SPI_plan_get_plan_sources"] =
			wasmExports["SPI_plan_get_plan_sources"])(a0));
	let _SPI_plan_get_cached_plan = (Module["_SPI_plan_get_cached_plan"] = (a0: any) =>
		(_SPI_plan_get_cached_plan = Module["_SPI_plan_get_cached_plan"] =
			wasmExports["SPI_plan_get_cached_plan"])(a0));
	let _SPI_register_relation = (Module["_SPI_register_relation"] = (a0: any) =>
		(_SPI_register_relation = Module["_SPI_register_relation"] =
			wasmExports["SPI_register_relation"])(a0));
	let _create_queryEnv = (Module["_create_queryEnv"] = () =>
		(_create_queryEnv = Module["_create_queryEnv"] =
			wasmExports["create_queryEnv"])());
	let _register_ENR = (Module["_register_ENR"] = (a0: any, a1: any) =>
		(_register_ENR = Module["_register_ENR"] = wasmExports["register_ENR"])(
			a0,
			a1,
		));
	let _SPI_register_trigger_data = (Module["_SPI_register_trigger_data"] = (
		a0,
	) =>
		(_SPI_register_trigger_data = Module["_SPI_register_trigger_data"] =
			wasmExports["SPI_register_trigger_data"])(a0));
	let _tuplestore_tuple_count = (Module["_tuplestore_tuple_count"] = (a0: any) =>
		(_tuplestore_tuple_count = Module["_tuplestore_tuple_count"] =
			wasmExports["tuplestore_tuple_count"])(a0));
	_GetUserMapping = (Module["_GetUserMapping"] = (a0: any, a1: any) =>
		(_GetUserMapping = Module["_GetUserMapping"] =
			wasmExports["GetUserMapping"])(a0, a1));
	_GetForeignTable = (Module["_GetForeignTable"] = (a0: any) =>
		(_GetForeignTable = Module["_GetForeignTable"] =
			wasmExports["GetForeignTable"])(a0));
	_GetForeignColumnOptions = (Module["_GetForeignColumnOptions"] = (
		a0,
		a1,
	) =>
		(_GetForeignColumnOptions = Module["_GetForeignColumnOptions"] =
			wasmExports["GetForeignColumnOptions"])(a0, a1));
	_initClosestMatch = (Module["_initClosestMatch"] = (a0: any, a1: any, a2: any) =>
		(_initClosestMatch = Module["_initClosestMatch"] =
			wasmExports["initClosestMatch"])(a0, a1, a2));
	_updateClosestMatch = (Module["_updateClosestMatch"] = (a0: any, a1: any) =>
		(_updateClosestMatch = Module["_updateClosestMatch"] =
			wasmExports["updateClosestMatch"])(a0, a1));
	_getClosestMatch = (Module["_getClosestMatch"] = (a0: any) =>
		(_getClosestMatch = Module["_getClosestMatch"] =
			wasmExports["getClosestMatch"])(a0));
	_GetExistingLocalJoinPath = (Module["_GetExistingLocalJoinPath"] = (a0: any) =>
		(_GetExistingLocalJoinPath = Module["_GetExistingLocalJoinPath"] =
			wasmExports["GetExistingLocalJoinPath"])(a0));
	let _bloom_create = (Module["_bloom_create"] = (a0: any, a1: any, a2: any) =>
		(_bloom_create = Module["_bloom_create"] = wasmExports["bloom_create"])(
			a0,
			a1,
			a2,
		));
	let _bloom_free = (Module["_bloom_free"] = (a0: any) =>
		(_bloom_free = Module["_bloom_free"] = wasmExports["bloom_free"])(a0));
	let _bloom_add_element = (Module["_bloom_add_element"] = (a0: any, a1: any, a2: any) =>
		(_bloom_add_element = Module["_bloom_add_element"] =
			wasmExports["bloom_add_element"])(a0, a1, a2));
	let _bloom_lacks_element = (Module["_bloom_lacks_element"] = (a0: any, a1: any, a2: any) =>
		(_bloom_lacks_element = Module["_bloom_lacks_element"] =
			wasmExports["bloom_lacks_element"])(a0, a1, a2));
	let _bloom_prop_bits_set = (Module["_bloom_prop_bits_set"] = (a0: any) =>
		(_bloom_prop_bits_set = Module["_bloom_prop_bits_set"] =
			wasmExports["bloom_prop_bits_set"])(a0));
	let _gai_strerror = (Module["_gai_strerror"] = (a0: any) =>
		(_gai_strerror = Module["_gai_strerror"] = wasmExports["gai_strerror"])(
			a0,
		));
	_socket = (Module["_socket"] = (a0: any, a1: any, a2: any) =>
		(_socket = Module["_socket"] = wasmExports["socket"])(a0, a1, a2));
	_connect = (Module["_connect"] = (a0: any, a1: any, a2: any) =>
		(_connect = Module["_connect"] = wasmExports["connect"])(a0, a1, a2));
	_send = (Module["_send"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_send = Module["_send"] = wasmExports["send"])(a0, a1, a2, a3));
	_recv = (Module["_recv"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_recv = Module["_recv"] = wasmExports["recv"])(a0, a1, a2, a3));
	let _be_lo_unlink = (Module["_be_lo_unlink"] = (a0: any) =>
		(_be_lo_unlink = Module["_be_lo_unlink"] = wasmExports["be_lo_unlink"])(
			a0,
		));
	let _text_to_cstring_buffer = (Module["_text_to_cstring_buffer"] = (
		a0,
		a1,
		a2,
	) =>
		(_text_to_cstring_buffer = Module["_text_to_cstring_buffer"] =
			wasmExports["text_to_cstring_buffer"])(a0, a1, a2));
	let _set_read_write_cbs = (Module["_set_read_write_cbs"] = (a0: any, a1: any) =>
		(_set_read_write_cbs = Module["_set_read_write_cbs"] =
			wasmExports["set_read_write_cbs"])(a0, a1));
	_setsockopt = (Module["_setsockopt"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_setsockopt = Module["_setsockopt"] = wasmExports["setsockopt"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_getsockopt = (Module["_getsockopt"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_getsockopt = Module["_getsockopt"] = wasmExports["getsockopt"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_getsockname = (Module["_getsockname"] = (a0: any, a1: any, a2: any) =>
		(_getsockname = Module["_getsockname"] = wasmExports["getsockname"])(
			a0,
			a1,
			a2,
		));
	_poll = (Module["_poll"] = (a0: any, a1: any, a2: any) =>
		(_poll = Module["_poll"] = wasmExports["poll"])(a0, a1, a2));
	let _pg_mb2wchar_with_len = (Module["_pg_mb2wchar_with_len"] = (a0: any, a1: any, a2: any) =>
		(_pg_mb2wchar_with_len = Module["_pg_mb2wchar_with_len"] =
			wasmExports["pg_mb2wchar_with_len"])(a0, a1, a2));
	let _pg_regcomp = (Module["_pg_regcomp"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_pg_regcomp = Module["_pg_regcomp"] = wasmExports["pg_regcomp"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _pg_regerror = (Module["_pg_regerror"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_regerror = Module["_pg_regerror"] = wasmExports["pg_regerror"])(
			a0,
			a1,
			a2,
			a3,
		));
	_strcat = (Module["_strcat"] = (a0: any, a1: any) =>
		(_strcat = Module["_strcat"] = wasmExports["strcat"])(a0, a1));
	let _pq_sendtext = (Module["_pq_sendtext"] = (a0: any, a1: any, a2: any) =>
		(_pq_sendtext = Module["_pq_sendtext"] = wasmExports["pq_sendtext"])(
			a0,
			a1,
			a2,
		));
	let _pq_sendfloat4 = (Module["_pq_sendfloat4"] = (a0: any, a1: any) =>
		(_pq_sendfloat4 = Module["_pq_sendfloat4"] = wasmExports["pq_sendfloat4"])(
			a0,
			a1,
		));
	let _pq_sendfloat8 = (Module["_pq_sendfloat8"] = (a0: any, a1: any) =>
		(_pq_sendfloat8 = Module["_pq_sendfloat8"] = wasmExports["pq_sendfloat8"])(
			a0,
			a1,
		));
	let _pq_begintypsend = (Module["_pq_begintypsend"] = (a0: any) =>
		(_pq_begintypsend = Module["_pq_begintypsend"] =
			wasmExports["pq_begintypsend"])(a0));
	let _pq_endtypsend = (Module["_pq_endtypsend"] = (a0: any) =>
		(_pq_endtypsend = Module["_pq_endtypsend"] = wasmExports["pq_endtypsend"])(
			a0,
		));
	let _pq_getmsgfloat4 = (Module["_pq_getmsgfloat4"] = (a0: any) =>
		(_pq_getmsgfloat4 = Module["_pq_getmsgfloat4"] =
			wasmExports["pq_getmsgfloat4"])(a0));
	let _pq_getmsgfloat8 = (Module["_pq_getmsgfloat8"] = (a0: any) =>
		(_pq_getmsgfloat8 = Module["_pq_getmsgfloat8"] =
			wasmExports["pq_getmsgfloat8"])(a0));
	let _pq_getmsgtext = (Module["_pq_getmsgtext"] = (a0: any, a1: any, a2: any) =>
		(_pq_getmsgtext = Module["_pq_getmsgtext"] = wasmExports["pq_getmsgtext"])(
			a0,
			a1,
			a2,
		));
	let _pg_strtoint32 = (Module["_pg_strtoint32"] = (a0: any) =>
		(_pg_strtoint32 = Module["_pg_strtoint32"] = wasmExports["pg_strtoint32"])(
			a0,
		));
	let _bms_membership = (Module["_bms_membership"] = (a0: any) =>
		(_bms_membership = Module["_bms_membership"] =
			wasmExports["bms_membership"])(a0));
	let _list_make5_impl = (Module["_list_make5_impl"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_list_make5_impl = Module["_list_make5_impl"] =
			wasmExports["list_make5_impl"])(a0, a1, a2, a3, a4, a5));
	let _list_insert_nth = (Module["_list_insert_nth"] = (a0: any, a1: any, a2: any) =>
		(_list_insert_nth = Module["_list_insert_nth"] =
			wasmExports["list_insert_nth"])(a0, a1, a2));
	let _list_member_ptr = (Module["_list_member_ptr"] = (a0: any, a1: any) =>
		(_list_member_ptr = Module["_list_member_ptr"] =
			wasmExports["list_member_ptr"])(a0, a1));
	let _list_append_unique_ptr = (Module["_list_append_unique_ptr"] = (a0: any, a1: any) =>
		(_list_append_unique_ptr = Module["_list_append_unique_ptr"] =
			wasmExports["list_append_unique_ptr"])(a0, a1));
	let _make_opclause = (Module["_make_opclause"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_make_opclause = Module["_make_opclause"] = wasmExports["make_opclause"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
		));
	_exprIsLengthCoercion = (Module["_exprIsLengthCoercion"] = (a0: any, a1: any) =>
		(_exprIsLengthCoercion = Module["_exprIsLengthCoercion"] =
			wasmExports["exprIsLengthCoercion"])(a0, a1));
	let _fix_opfuncids = (Module["_fix_opfuncids"] = (a0: any) =>
		(_fix_opfuncids = Module["_fix_opfuncids"] = wasmExports["fix_opfuncids"])(
			a0,
		));
	_CleanQuerytext = (Module["_CleanQuerytext"] = (a0: any, a1: any, a2: any) =>
		(_CleanQuerytext = Module["_CleanQuerytext"] =
			wasmExports["CleanQuerytext"])(a0, a1, a2));
	_EnableQueryId = (Module["_EnableQueryId"] = () =>
		(_EnableQueryId = Module["_EnableQueryId"] =
			wasmExports["EnableQueryId"])());
	let _find_base_rel = (Module["_find_base_rel"] = (a0: any, a1: any) =>
		(_find_base_rel = Module["_find_base_rel"] = wasmExports["find_base_rel"])(
			a0,
			a1,
		));
	let _add_path = (Module["_add_path"] = (a0: any, a1: any) =>
		(_add_path = Module["_add_path"] = wasmExports["add_path"])(a0, a1));
	let _pathkeys_contained_in = (Module["_pathkeys_contained_in"] = (a0: any, a1: any) =>
		(_pathkeys_contained_in = Module["_pathkeys_contained_in"] =
			wasmExports["pathkeys_contained_in"])(a0, a1));
	let _create_sort_path = (Module["_create_sort_path"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_create_sort_path = Module["_create_sort_path"] =
			wasmExports["create_sort_path"])(a0, a1, a2, a3, a4));
	let _set_baserel_size_estimates = (Module["_set_baserel_size_estimates"] = (
		a0,
		a1,
	) =>
		(_set_baserel_size_estimates = Module["_set_baserel_size_estimates"] =
			wasmExports["set_baserel_size_estimates"])(a0, a1));
	let _clauselist_selectivity = (Module["_clauselist_selectivity"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_clauselist_selectivity = Module["_clauselist_selectivity"] =
			wasmExports["clauselist_selectivity"])(a0, a1, a2, a3, a4));
	let _get_tablespace_page_costs = (Module["_get_tablespace_page_costs"] = (
		a0,
		a1,
		a2,
	) =>
		(_get_tablespace_page_costs = Module["_get_tablespace_page_costs"] =
			wasmExports["get_tablespace_page_costs"])(a0, a1, a2));
	let _cost_qual_eval = (Module["_cost_qual_eval"] = (a0: any, a1: any, a2: any) =>
		(_cost_qual_eval = Module["_cost_qual_eval"] =
			wasmExports["cost_qual_eval"])(a0, a1, a2));
	let _estimate_num_groups = (Module["_estimate_num_groups"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_estimate_num_groups = Module["_estimate_num_groups"] =
			wasmExports["estimate_num_groups"])(a0, a1, a2, a3, a4));
	let _cost_sort = (Module["_cost_sort"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
	) =>
		(_cost_sort = Module["_cost_sort"] = wasmExports["cost_sort"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
		));
	let _get_sortgrouplist_exprs = (Module["_get_sortgrouplist_exprs"] = (
		a0,
		a1,
	) =>
		(_get_sortgrouplist_exprs = Module["_get_sortgrouplist_exprs"] =
			wasmExports["get_sortgrouplist_exprs"])(a0, a1));
	let _make_restrictinfo = (Module["_make_restrictinfo"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
	) =>
		(_make_restrictinfo = Module["_make_restrictinfo"] =
			wasmExports["make_restrictinfo"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
		));
	let _generate_implied_equalities_for_column = (Module[
		"_generate_implied_equalities_for_column"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_generate_implied_equalities_for_column = Module[
			"_generate_implied_equalities_for_column"
		] =
			wasmExports["generate_implied_equalities_for_column"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _eclass_useful_for_merging = (Module["_eclass_useful_for_merging"] = (
		a0,
		a1,
		a2,
	) =>
		(_eclass_useful_for_merging = Module["_eclass_useful_for_merging"] =
			wasmExports["eclass_useful_for_merging"])(a0, a1, a2));
	let _join_clause_is_movable_to = (Module["_join_clause_is_movable_to"] = (
		a0,
		a1,
	) =>
		(_join_clause_is_movable_to = Module["_join_clause_is_movable_to"] =
			wasmExports["join_clause_is_movable_to"])(a0, a1));
	let _get_plan_rowmark = (Module["_get_plan_rowmark"] = (a0: any, a1: any) =>
		(_get_plan_rowmark = Module["_get_plan_rowmark"] =
			wasmExports["get_plan_rowmark"])(a0, a1));
	let _update_mergeclause_eclasses = (Module["_update_mergeclause_eclasses"] = (
		a0,
		a1,
	) =>
		(_update_mergeclause_eclasses = Module["_update_mergeclause_eclasses"] =
			wasmExports["update_mergeclause_eclasses"])(a0, a1));
	let _find_join_rel = (Module["_find_join_rel"] = (a0: any, a1: any) =>
		(_find_join_rel = Module["_find_join_rel"] = wasmExports["find_join_rel"])(
			a0,
			a1,
		));
	let _make_canonical_pathkey = (Module["_make_canonical_pathkey"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_make_canonical_pathkey = Module["_make_canonical_pathkey"] =
			wasmExports["make_canonical_pathkey"])(a0, a1, a2, a3, a4));
	let _get_sortgroupref_clause_noerr = (Module[
		"_get_sortgroupref_clause_noerr"
	] = (a0: any, a1: any) =>
		(_get_sortgroupref_clause_noerr = Module["_get_sortgroupref_clause_noerr"] =
			wasmExports["get_sortgroupref_clause_noerr"])(a0, a1));
	let _extract_actual_clauses = (Module["_extract_actual_clauses"] = (a0: any, a1: any) =>
		(_extract_actual_clauses = Module["_extract_actual_clauses"] =
			wasmExports["extract_actual_clauses"])(a0, a1));
	let _change_plan_targetlist = (Module["_change_plan_targetlist"] = (
		a0,
		a1,
		a2,
	) =>
		(_change_plan_targetlist = Module["_change_plan_targetlist"] =
			wasmExports["change_plan_targetlist"])(a0, a1, a2));
	let _make_foreignscan = (Module["_make_foreignscan"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_make_foreignscan = Module["_make_foreignscan"] =
			wasmExports["make_foreignscan"])(a0, a1, a2, a3, a4, a5, a6, a7));
	let _tlist_member = (Module["_tlist_member"] = (a0: any, a1: any) =>
		(_tlist_member = Module["_tlist_member"] = wasmExports["tlist_member"])(
			a0,
			a1,
		));
	let _pull_vars_of_level = (Module["_pull_vars_of_level"] = (a0: any, a1: any) =>
		(_pull_vars_of_level = Module["_pull_vars_of_level"] =
			wasmExports["pull_vars_of_level"])(a0, a1));
	_IncrementVarSublevelsUp = (Module["_IncrementVarSublevelsUp"] = (
		a0,
		a1,
		a2,
	) =>
		(_IncrementVarSublevelsUp = Module["_IncrementVarSublevelsUp"] =
			wasmExports["IncrementVarSublevelsUp"])(a0, a1, a2));
	let _standard_planner = (Module["_standard_planner"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_standard_planner = Module["_standard_planner"] =
			wasmExports["standard_planner"])(a0, a1, a2, a3));
	let _get_relids_in_jointree = (Module["_get_relids_in_jointree"] = (
		a0,
		a1,
		a2,
	) =>
		(_get_relids_in_jointree = Module["_get_relids_in_jointree"] =
			wasmExports["get_relids_in_jointree"])(a0, a1, a2));
	let _add_new_columns_to_pathtarget = (Module[
		"_add_new_columns_to_pathtarget"
	] = (a0: any, a1: any) =>
		(_add_new_columns_to_pathtarget = Module["_add_new_columns_to_pathtarget"] =
			wasmExports["add_new_columns_to_pathtarget"])(a0, a1));
	let _get_agg_clause_costs = (Module["_get_agg_clause_costs"] = (a0: any, a1: any, a2: any) =>
		(_get_agg_clause_costs = Module["_get_agg_clause_costs"] =
			wasmExports["get_agg_clause_costs"])(a0, a1, a2));
	let _grouping_is_sortable = (Module["_grouping_is_sortable"] = (a0: any) =>
		(_grouping_is_sortable = Module["_grouping_is_sortable"] =
			wasmExports["grouping_is_sortable"])(a0));
	let _copy_pathtarget = (Module["_copy_pathtarget"] = (a0: any) =>
		(_copy_pathtarget = Module["_copy_pathtarget"] =
			wasmExports["copy_pathtarget"])(a0));
	let _create_projection_path = (Module["_create_projection_path"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_create_projection_path = Module["_create_projection_path"] =
			wasmExports["create_projection_path"])(a0, a1, a2, a3));
	_GetSysCacheHashValue = (Module["_GetSysCacheHashValue"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_GetSysCacheHashValue = Module["_GetSysCacheHashValue"] =
			wasmExports["GetSysCacheHashValue"])(a0, a1, a2, a3, a4));
	let _get_translated_update_targetlist = (Module[
		"_get_translated_update_targetlist"
	] = (a0: any, a1: any, a2: any, a3: any) =>
		(_get_translated_update_targetlist = Module[
			"_get_translated_update_targetlist"
		] =
			wasmExports["get_translated_update_targetlist"])(a0, a1, a2, a3));
	let _add_row_identity_var = (Module["_add_row_identity_var"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_add_row_identity_var = Module["_add_row_identity_var"] =
			wasmExports["add_row_identity_var"])(a0, a1, a2, a3));
	let _get_rel_all_updated_cols = (Module["_get_rel_all_updated_cols"] = (
		a0,
		a1,
	) =>
		(_get_rel_all_updated_cols = Module["_get_rel_all_updated_cols"] =
			wasmExports["get_rel_all_updated_cols"])(a0, a1));
	let _get_baserel_parampathinfo = (Module["_get_baserel_parampathinfo"] = (
		a0,
		a1,
		a2,
	) =>
		(_get_baserel_parampathinfo = Module["_get_baserel_parampathinfo"] =
			wasmExports["get_baserel_parampathinfo"])(a0, a1, a2));
	let _create_foreignscan_path = (Module["_create_foreignscan_path"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
	) =>
		(_create_foreignscan_path = Module["_create_foreignscan_path"] =
			wasmExports["create_foreignscan_path"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
		));
	let _create_foreign_join_path = (Module["_create_foreign_join_path"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
	) =>
		(_create_foreign_join_path = Module["_create_foreign_join_path"] =
			wasmExports["create_foreign_join_path"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
		));
	let _create_foreign_upper_path = (Module["_create_foreign_upper_path"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
	) =>
		(_create_foreign_upper_path = Module["_create_foreign_upper_path"] =
			wasmExports["create_foreign_upper_path"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
		));
	let _adjust_limit_rows_costs = (Module["_adjust_limit_rows_costs"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_adjust_limit_rows_costs = Module["_adjust_limit_rows_costs"] =
			wasmExports["adjust_limit_rows_costs"])(a0, a1, a2, a3, a4));
	let _add_to_flat_tlist = (Module["_add_to_flat_tlist"] = (a0: any, a1: any) =>
		(_add_to_flat_tlist = Module["_add_to_flat_tlist"] =
			wasmExports["add_to_flat_tlist"])(a0, a1));
	let _get_fn_expr_argtype = (Module["_get_fn_expr_argtype"] = (a0: any, a1: any) =>
		(_get_fn_expr_argtype = Module["_get_fn_expr_argtype"] =
			wasmExports["get_fn_expr_argtype"])(a0, a1));
	let _on_shmem_exit = (Module["_on_shmem_exit"] = (a0: any, a1: any) =>
		(_on_shmem_exit = Module["_on_shmem_exit"] = wasmExports["on_shmem_exit"])(
			a0,
			a1,
		));
	_SignalHandlerForConfigReload = (Module["_SignalHandlerForConfigReload"] =
		(a0: any) =>
			(_SignalHandlerForConfigReload = Module["_SignalHandlerForConfigReload"] =
				wasmExports["SignalHandlerForConfigReload"])(a0));
	_SignalHandlerForShutdownRequest = (Module[
		"_SignalHandlerForShutdownRequest"
	] = (a0: any) =>
		(_SignalHandlerForShutdownRequest = Module[
			"_SignalHandlerForShutdownRequest"
		] =
			wasmExports["SignalHandlerForShutdownRequest"])(a0));
	let _procsignal_sigusr1_handler = (Module["_procsignal_sigusr1_handler"] = (
		a0,
	) =>
		(_procsignal_sigusr1_handler = Module["_procsignal_sigusr1_handler"] =
			wasmExports["procsignal_sigusr1_handler"])(a0));
	_RegisterBackgroundWorker = (Module["_RegisterBackgroundWorker"] = (a0: any) =>
		(_RegisterBackgroundWorker = Module["_RegisterBackgroundWorker"] =
			wasmExports["RegisterBackgroundWorker"])(a0));
	_WaitForBackgroundWorkerStartup = (Module[
		"_WaitForBackgroundWorkerStartup"
	] = (a0: any, a1: any) =>
		(_WaitForBackgroundWorkerStartup = Module[
			"_WaitForBackgroundWorkerStartup"
		] =
			wasmExports["WaitForBackgroundWorkerStartup"])(a0, a1));
	_GetConfigOption = (Module["_GetConfigOption"] = (a0: any, a1: any, a2: any) =>
		(_GetConfigOption = Module["_GetConfigOption"] =
			wasmExports["GetConfigOption"])(a0, a1, a2));
	_toupper = (Module["_toupper"] = (a0: any) =>
		(_toupper = Module["_toupper"] = wasmExports["toupper"])(a0));
	let _pg_reg_getinitialstate = (Module["_pg_reg_getinitialstate"] = (a0: any) =>
		(_pg_reg_getinitialstate = Module["_pg_reg_getinitialstate"] =
			wasmExports["pg_reg_getinitialstate"])(a0));
	let _pg_reg_getfinalstate = (Module["_pg_reg_getfinalstate"] = (a0: any) =>
		(_pg_reg_getfinalstate = Module["_pg_reg_getfinalstate"] =
			wasmExports["pg_reg_getfinalstate"])(a0));
	let _pg_reg_getnumoutarcs = (Module["_pg_reg_getnumoutarcs"] = (a0: any, a1: any) =>
		(_pg_reg_getnumoutarcs = Module["_pg_reg_getnumoutarcs"] =
			wasmExports["pg_reg_getnumoutarcs"])(a0, a1));
	let _pg_reg_getoutarcs = (Module["_pg_reg_getoutarcs"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_pg_reg_getoutarcs = Module["_pg_reg_getoutarcs"] =
			wasmExports["pg_reg_getoutarcs"])(a0, a1, a2, a3));
	let _pg_reg_getnumcolors = (Module["_pg_reg_getnumcolors"] = (a0: any) =>
		(_pg_reg_getnumcolors = Module["_pg_reg_getnumcolors"] =
			wasmExports["pg_reg_getnumcolors"])(a0));
	let _pg_reg_colorisbegin = (Module["_pg_reg_colorisbegin"] = (a0: any, a1: any) =>
		(_pg_reg_colorisbegin = Module["_pg_reg_colorisbegin"] =
			wasmExports["pg_reg_colorisbegin"])(a0, a1));
	let _pg_reg_colorisend = (Module["_pg_reg_colorisend"] = (a0: any, a1: any) =>
		(_pg_reg_colorisend = Module["_pg_reg_colorisend"] =
			wasmExports["pg_reg_colorisend"])(a0, a1));
	let _pg_reg_getnumcharacters = (Module["_pg_reg_getnumcharacters"] = (
		a0,
		a1,
	) =>
		(_pg_reg_getnumcharacters = Module["_pg_reg_getnumcharacters"] =
			wasmExports["pg_reg_getnumcharacters"])(a0, a1));
	let _pg_reg_getcharacters = (Module["_pg_reg_getcharacters"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_pg_reg_getcharacters = Module["_pg_reg_getcharacters"] =
			wasmExports["pg_reg_getcharacters"])(a0, a1, a2, a3));
	_OutputPluginPrepareWrite = (Module["_OutputPluginPrepareWrite"] = (
		a0,
		a1,
	) =>
		(_OutputPluginPrepareWrite = Module["_OutputPluginPrepareWrite"] =
			wasmExports["OutputPluginPrepareWrite"])(a0, a1));
	_OutputPluginWrite = (Module["_OutputPluginWrite"] = (a0: any, a1: any) =>
		(_OutputPluginWrite = Module["_OutputPluginWrite"] =
			wasmExports["OutputPluginWrite"])(a0, a1));
	let _array_contains_nulls = (Module["_array_contains_nulls"] = (a0: any) =>
		(_array_contains_nulls = Module["_array_contains_nulls"] =
			wasmExports["array_contains_nulls"])(a0));
	let _hash_seq_term = (Module["_hash_seq_term"] = (a0: any) =>
		(_hash_seq_term = Module["_hash_seq_term"] = wasmExports["hash_seq_term"])(
			a0,
		));
	_FreeErrorData = (Module["_FreeErrorData"] = (a0: any) =>
		(_FreeErrorData = Module["_FreeErrorData"] = wasmExports["FreeErrorData"])(
			a0,
		));
	_RelidByRelfilenumber = (Module["_RelidByRelfilenumber"] = (a0: any, a1: any) =>
		(_RelidByRelfilenumber = Module["_RelidByRelfilenumber"] =
			wasmExports["RelidByRelfilenumber"])(a0, a1));
	_WaitLatchOrSocket = (Module["_WaitLatchOrSocket"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_WaitLatchOrSocket = Module["_WaitLatchOrSocket"] =
			wasmExports["WaitLatchOrSocket"])(a0, a1, a2, a3, a4));
	let _get_row_security_policies = (Module["_get_row_security_policies"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
	) =>
		(_get_row_security_policies = Module["_get_row_security_policies"] =
			wasmExports["get_row_security_policies"])(a0, a1, a2, a3, a4, a5, a6));
	let _hash_estimate_size = (Module["_hash_estimate_size"] = (a0: any, a1: any) =>
		(_hash_estimate_size = Module["_hash_estimate_size"] =
			wasmExports["hash_estimate_size"])(a0, a1));
	_ShmemInitHash = (Module["_ShmemInitHash"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_ShmemInitHash = Module["_ShmemInitHash"] = wasmExports["ShmemInitHash"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_LockBufHdr = (Module["_LockBufHdr"] = (a0: any) =>
		(_LockBufHdr = Module["_LockBufHdr"] = wasmExports["LockBufHdr"])(a0));
	_EvictUnpinnedBuffer = (Module["_EvictUnpinnedBuffer"] = (a0: any) =>
		(_EvictUnpinnedBuffer = Module["_EvictUnpinnedBuffer"] =
			wasmExports["EvictUnpinnedBuffer"])(a0));
	let _have_free_buffer = (Module["_have_free_buffer"] = () =>
		(_have_free_buffer = Module["_have_free_buffer"] =
			wasmExports["have_free_buffer"])());
	let _copy_file = (Module["_copy_file"] = (a0: any, a1: any) =>
		(_copy_file = Module["_copy_file"] = wasmExports["copy_file"])(a0, a1));
	_AcquireExternalFD = (Module["_AcquireExternalFD"] = () =>
		(_AcquireExternalFD = Module["_AcquireExternalFD"] =
			wasmExports["AcquireExternalFD"])());
	_GetNamedDSMSegment = (Module["_GetNamedDSMSegment"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_GetNamedDSMSegment = Module["_GetNamedDSMSegment"] =
			wasmExports["GetNamedDSMSegment"])(a0, a1, a2, a3));
	_RequestAddinShmemSpace = (Module["_RequestAddinShmemSpace"] = (a0: any) =>
		(_RequestAddinShmemSpace = Module["_RequestAddinShmemSpace"] =
			wasmExports["RequestAddinShmemSpace"])(a0));
	_GetRunningTransactionData = (Module["_GetRunningTransactionData"] = () =>
		(_GetRunningTransactionData = Module["_GetRunningTransactionData"] =
			wasmExports["GetRunningTransactionData"])());
	_BackendXidGetPid = (Module["_BackendXidGetPid"] = (a0: any) =>
		(_BackendXidGetPid = Module["_BackendXidGetPid"] =
			wasmExports["BackendXidGetPid"])(a0));
	_LWLockRegisterTranche = (Module["_LWLockRegisterTranche"] = (a0: any, a1: any) =>
		(_LWLockRegisterTranche = Module["_LWLockRegisterTranche"] =
			wasmExports["LWLockRegisterTranche"])(a0, a1));
	_GetNamedLWLockTranche = (Module["_GetNamedLWLockTranche"] = (a0: any) =>
		(_GetNamedLWLockTranche = Module["_GetNamedLWLockTranche"] =
			wasmExports["GetNamedLWLockTranche"])(a0));
	_LWLockNewTrancheId = (Module["_LWLockNewTrancheId"] = () =>
		(_LWLockNewTrancheId = Module["_LWLockNewTrancheId"] =
			wasmExports["LWLockNewTrancheId"])());
	_RequestNamedLWLockTranche = (Module["_RequestNamedLWLockTranche"] = (
		a0,
		a1,
	) =>
		(_RequestNamedLWLockTranche = Module["_RequestNamedLWLockTranche"] =
			wasmExports["RequestNamedLWLockTranche"])(a0, a1));
	let _standard_ProcessUtility = (Module["_standard_ProcessUtility"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_standard_ProcessUtility = Module["_standard_ProcessUtility"] =
			wasmExports["standard_ProcessUtility"])(a0, a1, a2, a3, a4, a5, a6, a7));
	let _lookup_ts_dictionary_cache = (Module["_lookup_ts_dictionary_cache"] = (
		a0,
	) =>
		(_lookup_ts_dictionary_cache = Module["_lookup_ts_dictionary_cache"] =
			wasmExports["lookup_ts_dictionary_cache"])(a0));
	let _get_tsearch_config_filename = (Module["_get_tsearch_config_filename"] = (
		a0,
		a1,
	) =>
		(_get_tsearch_config_filename = Module["_get_tsearch_config_filename"] =
			wasmExports["get_tsearch_config_filename"])(a0, a1));
	_lowerstr = (Module["_lowerstr"] = (a0: any) =>
		(_lowerstr = Module["_lowerstr"] = wasmExports["lowerstr"])(a0));
	_readstoplist = (Module["_readstoplist"] = (a0: any, a1: any, a2: any) =>
		(_readstoplist = Module["_readstoplist"] = wasmExports["readstoplist"])(
			a0,
			a1,
			a2,
		));
	let _lowerstr_with_len = (Module["_lowerstr_with_len"] = (a0: any, a1: any) =>
		(_lowerstr_with_len = Module["_lowerstr_with_len"] =
			wasmExports["lowerstr_with_len"])(a0, a1));
	_searchstoplist = (Module["_searchstoplist"] = (a0: any, a1: any) =>
		(_searchstoplist = Module["_searchstoplist"] =
			wasmExports["searchstoplist"])(a0, a1));
	let _tsearch_readline_begin = (Module["_tsearch_readline_begin"] = (a0: any, a1: any) =>
		(_tsearch_readline_begin = Module["_tsearch_readline_begin"] =
			wasmExports["tsearch_readline_begin"])(a0, a1));
	let _tsearch_readline = (Module["_tsearch_readline"] = (a0: any) =>
		(_tsearch_readline = Module["_tsearch_readline"] =
			wasmExports["tsearch_readline"])(a0));
	let _t_isspace = (Module["_t_isspace"] = (a0: any) =>
		(_t_isspace = Module["_t_isspace"] = wasmExports["t_isspace"])(a0));
	let _tsearch_readline_end = (Module["_tsearch_readline_end"] = (a0: any) =>
		(_tsearch_readline_end = Module["_tsearch_readline_end"] =
			wasmExports["tsearch_readline_end"])(a0));
	_stringToQualifiedNameList = (Module["_stringToQualifiedNameList"] = (
		a0,
		a1,
	) =>
		(_stringToQualifiedNameList = Module["_stringToQualifiedNameList"] =
			wasmExports["stringToQualifiedNameList"])(a0, a1));
	let _t_isdigit = (Module["_t_isdigit"] = (a0: any) =>
		(_t_isdigit = Module["_t_isdigit"] = wasmExports["t_isdigit"])(a0));
	let _t_isalnum = (Module["_t_isalnum"] = (a0: any) =>
		(_t_isalnum = Module["_t_isalnum"] = wasmExports["t_isalnum"])(a0));
	let _get_restriction_variable = (Module["_get_restriction_variable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
	) =>
		(_get_restriction_variable = Module["_get_restriction_variable"] =
			wasmExports["get_restriction_variable"])(a0, a1, a2, a3, a4, a5));
	_MemoryContextAllocHuge = (Module["_MemoryContextAllocHuge"] = (a0: any, a1: any) =>
		(_MemoryContextAllocHuge = Module["_MemoryContextAllocHuge"] =
			wasmExports["MemoryContextAllocHuge"])(a0, a1));
	_WaitEventExtensionNew = (Module["_WaitEventExtensionNew"] = (a0: any) =>
		(_WaitEventExtensionNew = Module["_WaitEventExtensionNew"] =
			wasmExports["WaitEventExtensionNew"])(a0));
	let _expand_array = (Module["_expand_array"] = (a0: any, a1: any, a2: any) =>
		(_expand_array = Module["_expand_array"] = wasmExports["expand_array"])(
			a0,
			a1,
			a2,
		));
	_arraycontsel = (Module["_arraycontsel"] = (a0: any) =>
		(_arraycontsel = Module["_arraycontsel"] = wasmExports["arraycontsel"])(
			a0,
		));
	_arraycontjoinsel = (Module["_arraycontjoinsel"] = (a0: any) =>
		(_arraycontjoinsel = Module["_arraycontjoinsel"] =
			wasmExports["arraycontjoinsel"])(a0));
	_initArrayResult = (Module["_initArrayResult"] = (a0: any, a1: any, a2: any) =>
		(_initArrayResult = Module["_initArrayResult"] =
			wasmExports["initArrayResult"])(a0, a1, a2));
	let _array_create_iterator = (Module["_array_create_iterator"] = (
		a0,
		a1,
		a2,
	) =>
		(_array_create_iterator = Module["_array_create_iterator"] =
			wasmExports["array_create_iterator"])(a0, a1, a2));
	let _array_iterate = (Module["_array_iterate"] = (a0: any, a1: any, a2: any) =>
		(_array_iterate = Module["_array_iterate"] = wasmExports["array_iterate"])(
			a0,
			a1,
			a2,
		));
	_ArrayGetIntegerTypmods = (Module["_ArrayGetIntegerTypmods"] = (a0: any, a1: any) =>
		(_ArrayGetIntegerTypmods = Module["_ArrayGetIntegerTypmods"] =
			wasmExports["ArrayGetIntegerTypmods"])(a0, a1));
	_boolin = (Module["_boolin"] = (a0: any) =>
		(_boolin = Module["_boolin"] = wasmExports["boolin"])(a0));
	let _cash_cmp = (Module["_cash_cmp"] = (a0: any) =>
		(_cash_cmp = Module["_cash_cmp"] = wasmExports["cash_cmp"])(a0));
	let _int64_to_numeric = (Module["_int64_to_numeric"] = (a0: any) =>
		(_int64_to_numeric = Module["_int64_to_numeric"] =
			wasmExports["int64_to_numeric"])(a0));
	let _numeric_div = (Module["_numeric_div"] = (a0: any) =>
		(_numeric_div = Module["_numeric_div"] = wasmExports["numeric_div"])(a0));
	let _date_eq = (Module["_date_eq"] = (a0: any) =>
		(_date_eq = Module["_date_eq"] = wasmExports["date_eq"])(a0));
	let _date_lt = (Module["_date_lt"] = (a0: any) =>
		(_date_lt = Module["_date_lt"] = wasmExports["date_lt"])(a0));
	let _date_le = (Module["_date_le"] = (a0: any) =>
		(_date_le = Module["_date_le"] = wasmExports["date_le"])(a0));
	let _date_gt = (Module["_date_gt"] = (a0: any) =>
		(_date_gt = Module["_date_gt"] = wasmExports["date_gt"])(a0));
	let _date_ge = (Module["_date_ge"] = (a0: any) =>
		(_date_ge = Module["_date_ge"] = wasmExports["date_ge"])(a0));
	let _date_cmp = (Module["_date_cmp"] = (a0: any) =>
		(_date_cmp = Module["_date_cmp"] = wasmExports["date_cmp"])(a0));
	let _date_mi = (Module["_date_mi"] = (a0: any) =>
		(_date_mi = Module["_date_mi"] = wasmExports["date_mi"])(a0));
	let _time_eq = (Module["_time_eq"] = (a0: any) =>
		(_time_eq = Module["_time_eq"] = wasmExports["time_eq"])(a0));
	let _time_lt = (Module["_time_lt"] = (a0: any) =>
		(_time_lt = Module["_time_lt"] = wasmExports["time_lt"])(a0));
	let _time_le = (Module["_time_le"] = (a0: any) =>
		(_time_le = Module["_time_le"] = wasmExports["time_le"])(a0));
	let _time_gt = (Module["_time_gt"] = (a0: any) =>
		(_time_gt = Module["_time_gt"] = wasmExports["time_gt"])(a0));
	let _time_ge = (Module["_time_ge"] = (a0: any) =>
		(_time_ge = Module["_time_ge"] = wasmExports["time_ge"])(a0));
	let _time_cmp = (Module["_time_cmp"] = (a0: any) =>
		(_time_cmp = Module["_time_cmp"] = wasmExports["time_cmp"])(a0));
	let _time_mi_time = (Module["_time_mi_time"] = (a0: any) =>
		(_time_mi_time = Module["_time_mi_time"] = wasmExports["time_mi_time"])(
			a0,
		));
	let _timetz_cmp = (Module["_timetz_cmp"] = (a0: any) =>
		(_timetz_cmp = Module["_timetz_cmp"] = wasmExports["timetz_cmp"])(a0));
	_TransferExpandedObject = (Module["_TransferExpandedObject"] = (a0: any, a1: any) =>
		(_TransferExpandedObject = Module["_TransferExpandedObject"] =
			wasmExports["TransferExpandedObject"])(a0, a1));
	let _numeric_lt = (Module["_numeric_lt"] = (a0: any) =>
		(_numeric_lt = Module["_numeric_lt"] = wasmExports["numeric_lt"])(a0));
	let _numeric_ge = (Module["_numeric_ge"] = (a0: any) =>
		(_numeric_ge = Module["_numeric_ge"] = wasmExports["numeric_ge"])(a0));
	let _err_generic_string = (Module["_err_generic_string"] = (a0: any, a1: any) =>
		(_err_generic_string = Module["_err_generic_string"] =
			wasmExports["err_generic_string"])(a0, a1));
	let _domain_check = (Module["_domain_check"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_domain_check = Module["_domain_check"] = wasmExports["domain_check"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _enum_lt = (Module["_enum_lt"] = (a0: any) =>
		(_enum_lt = Module["_enum_lt"] = wasmExports["enum_lt"])(a0));
	let _enum_le = (Module["_enum_le"] = (a0: any) =>
		(_enum_le = Module["_enum_le"] = wasmExports["enum_le"])(a0));
	let _enum_ge = (Module["_enum_ge"] = (a0: any) =>
		(_enum_ge = Module["_enum_ge"] = wasmExports["enum_ge"])(a0));
	let _enum_gt = (Module["_enum_gt"] = (a0: any) =>
		(_enum_gt = Module["_enum_gt"] = wasmExports["enum_gt"])(a0));
	let _enum_cmp = (Module["_enum_cmp"] = (a0: any) =>
		(_enum_cmp = Module["_enum_cmp"] = wasmExports["enum_cmp"])(a0));
	let _make_expanded_record_from_typeid = (Module[
		"_make_expanded_record_from_typeid"
	] = (a0: any, a1: any, a2: any) =>
		(_make_expanded_record_from_typeid = Module[
			"_make_expanded_record_from_typeid"
		] =
			wasmExports["make_expanded_record_from_typeid"])(a0, a1, a2));
	let _make_expanded_record_from_tupdesc = (Module[
		"_make_expanded_record_from_tupdesc"
	] = (a0: any, a1: any) =>
		(_make_expanded_record_from_tupdesc = Module[
			"_make_expanded_record_from_tupdesc"
		] =
			wasmExports["make_expanded_record_from_tupdesc"])(a0, a1));
	let _make_expanded_record_from_exprecord = (Module[
		"_make_expanded_record_from_exprecord"
	] = (a0: any, a1: any) =>
		(_make_expanded_record_from_exprecord = Module[
			"_make_expanded_record_from_exprecord"
		] =
			wasmExports["make_expanded_record_from_exprecord"])(a0, a1));
	let _expanded_record_set_tuple = (Module["_expanded_record_set_tuple"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_expanded_record_set_tuple = Module["_expanded_record_set_tuple"] =
			wasmExports["expanded_record_set_tuple"])(a0, a1, a2, a3));
	let _expanded_record_get_tuple = (Module["_expanded_record_get_tuple"] = (
		a0,
	) =>
		(_expanded_record_get_tuple = Module["_expanded_record_get_tuple"] =
			wasmExports["expanded_record_get_tuple"])(a0));
	let _deconstruct_expanded_record = (Module["_deconstruct_expanded_record"] = (
		a0,
	) =>
		(_deconstruct_expanded_record = Module["_deconstruct_expanded_record"] =
			wasmExports["deconstruct_expanded_record"])(a0));
	let _expanded_record_lookup_field = (Module["_expanded_record_lookup_field"] =
		(a0: any, a1: any, a2: any) =>
			(_expanded_record_lookup_field = Module["_expanded_record_lookup_field"] =
				wasmExports["expanded_record_lookup_field"])(a0, a1, a2));
	let _expanded_record_set_field_internal = (Module[
		"_expanded_record_set_field_internal"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_expanded_record_set_field_internal = Module[
			"_expanded_record_set_field_internal"
		] =
			wasmExports["expanded_record_set_field_internal"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
		));
	let _expanded_record_set_fields = (Module["_expanded_record_set_fields"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_expanded_record_set_fields = Module["_expanded_record_set_fields"] =
			wasmExports["expanded_record_set_fields"])(a0, a1, a2, a3));
	let _float4in_internal = (Module["_float4in_internal"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_float4in_internal = Module["_float4in_internal"] =
			wasmExports["float4in_internal"])(a0, a1, a2, a3, a4));
	_strtof = (Module["_strtof"] = (a0: any, a1: any) =>
		(_strtof = Module["_strtof"] = wasmExports["strtof"])(a0, a1));
	let _float8in_internal = (Module["_float8in_internal"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_float8in_internal = Module["_float8in_internal"] =
			wasmExports["float8in_internal"])(a0, a1, a2, a3, a4));
	let _float8out_internal = (Module["_float8out_internal"] = (a0: any) =>
		(_float8out_internal = Module["_float8out_internal"] =
			wasmExports["float8out_internal"])(a0));
	_btfloat4cmp = (Module["_btfloat4cmp"] = (a0: any) =>
		(_btfloat4cmp = Module["_btfloat4cmp"] = wasmExports["btfloat4cmp"])(a0));
	_btfloat8cmp = (Module["_btfloat8cmp"] = (a0: any) =>
		(_btfloat8cmp = Module["_btfloat8cmp"] = wasmExports["btfloat8cmp"])(a0));
	_acos = (Module["_acos"] = (a0: any) =>
		(_acos = Module["_acos"] = wasmExports["acos"])(a0));
	_asin = (Module["_asin"] = (a0: any) =>
		(_asin = Module["_asin"] = wasmExports["asin"])(a0));
	_cos = (Module["_cos"] = (a0: any) =>
		(_cos = Module["_cos"] = wasmExports["cos"])(a0));
	let _str_tolower = (Module["_str_tolower"] = (a0: any, a1: any, a2: any) =>
		(_str_tolower = Module["_str_tolower"] = wasmExports["str_tolower"])(
			a0,
			a1,
			a2,
		));
	_pushJsonbValue = (Module["_pushJsonbValue"] = (a0: any, a1: any, a2: any) =>
		(_pushJsonbValue = Module["_pushJsonbValue"] =
			wasmExports["pushJsonbValue"])(a0, a1, a2));
	let _numeric_float4 = (Module["_numeric_float4"] = (a0: any) =>
		(_numeric_float4 = Module["_numeric_float4"] =
			wasmExports["numeric_float4"])(a0));
	let _numeric_cmp = (Module["_numeric_cmp"] = (a0: any) =>
		(_numeric_cmp = Module["_numeric_cmp"] = wasmExports["numeric_cmp"])(a0));
	let _numeric_eq = (Module["_numeric_eq"] = (a0: any) =>
		(_numeric_eq = Module["_numeric_eq"] = wasmExports["numeric_eq"])(a0));
	let _numeric_is_nan = (Module["_numeric_is_nan"] = (a0: any) =>
		(_numeric_is_nan = Module["_numeric_is_nan"] =
			wasmExports["numeric_is_nan"])(a0));
	let _timestamp_cmp = (Module["_timestamp_cmp"] = (a0: any) =>
		(_timestamp_cmp = Module["_timestamp_cmp"] = wasmExports["timestamp_cmp"])(
			a0,
		));
	let _macaddr_cmp = (Module["_macaddr_cmp"] = (a0: any) =>
		(_macaddr_cmp = Module["_macaddr_cmp"] = wasmExports["macaddr_cmp"])(a0));
	let _macaddr_lt = (Module["_macaddr_lt"] = (a0: any) =>
		(_macaddr_lt = Module["_macaddr_lt"] = wasmExports["macaddr_lt"])(a0));
	let _macaddr_le = (Module["_macaddr_le"] = (a0: any) =>
		(_macaddr_le = Module["_macaddr_le"] = wasmExports["macaddr_le"])(a0));
	let _macaddr_eq = (Module["_macaddr_eq"] = (a0: any) =>
		(_macaddr_eq = Module["_macaddr_eq"] = wasmExports["macaddr_eq"])(a0));
	let _macaddr_ge = (Module["_macaddr_ge"] = (a0: any) =>
		(_macaddr_ge = Module["_macaddr_ge"] = wasmExports["macaddr_ge"])(a0));
	let _macaddr_gt = (Module["_macaddr_gt"] = (a0: any) =>
		(_macaddr_gt = Module["_macaddr_gt"] = wasmExports["macaddr_gt"])(a0));
	let _macaddr8_cmp = (Module["_macaddr8_cmp"] = (a0: any) =>
		(_macaddr8_cmp = Module["_macaddr8_cmp"] = wasmExports["macaddr8_cmp"])(
			a0,
		));
	let _macaddr8_lt = (Module["_macaddr8_lt"] = (a0: any) =>
		(_macaddr8_lt = Module["_macaddr8_lt"] = wasmExports["macaddr8_lt"])(a0));
	let _macaddr8_le = (Module["_macaddr8_le"] = (a0: any) =>
		(_macaddr8_le = Module["_macaddr8_le"] = wasmExports["macaddr8_le"])(a0));
	let _macaddr8_eq = (Module["_macaddr8_eq"] = (a0: any) =>
		(_macaddr8_eq = Module["_macaddr8_eq"] = wasmExports["macaddr8_eq"])(a0));
	let _macaddr8_ge = (Module["_macaddr8_ge"] = (a0: any) =>
		(_macaddr8_ge = Module["_macaddr8_ge"] = wasmExports["macaddr8_ge"])(a0));
	let _macaddr8_gt = (Module["_macaddr8_gt"] = (a0: any) =>
		(_macaddr8_gt = Module["_macaddr8_gt"] = wasmExports["macaddr8_gt"])(a0));
	let _current_query = (Module["_current_query"] = (a0: any) =>
		(_current_query = Module["_current_query"] = wasmExports["current_query"])(
			a0,
		));
	let _unpack_sql_state = (Module["_unpack_sql_state"] = (a0: any) =>
		(_unpack_sql_state = Module["_unpack_sql_state"] =
			wasmExports["unpack_sql_state"])(a0));
	let _get_fn_expr_rettype = (Module["_get_fn_expr_rettype"] = (a0: any) =>
		(_get_fn_expr_rettype = Module["_get_fn_expr_rettype"] =
			wasmExports["get_fn_expr_rettype"])(a0));
	_btnamecmp = (Module["_btnamecmp"] = (a0: any) =>
		(_btnamecmp = Module["_btnamecmp"] = wasmExports["btnamecmp"])(a0));
	let _inet_in = (Module["_inet_in"] = (a0: any) =>
		(_inet_in = Module["_inet_in"] = wasmExports["inet_in"])(a0));
	let _network_cmp = (Module["_network_cmp"] = (a0: any) =>
		(_network_cmp = Module["_network_cmp"] = wasmExports["network_cmp"])(a0));
	let _convert_network_to_scalar = (Module["_convert_network_to_scalar"] = (
		a0,
		a1,
		a2,
	) =>
		(_convert_network_to_scalar = Module["_convert_network_to_scalar"] =
			wasmExports["convert_network_to_scalar"])(a0, a1, a2));
	let _numeric_gt = (Module["_numeric_gt"] = (a0: any) =>
		(_numeric_gt = Module["_numeric_gt"] = wasmExports["numeric_gt"])(a0));
	let _numeric_le = (Module["_numeric_le"] = (a0: any) =>
		(_numeric_le = Module["_numeric_le"] = wasmExports["numeric_le"])(a0));
	let _numeric_float8_no_overflow = (Module["_numeric_float8_no_overflow"] = (
		a0,
	) =>
		(_numeric_float8_no_overflow = Module["_numeric_float8_no_overflow"] =
			wasmExports["numeric_float8_no_overflow"])(a0));
	_oidout = (Module["_oidout"] = (a0: any) =>
		(_oidout = Module["_oidout"] = wasmExports["oidout"])(a0));
	let _interval_mi = (Module["_interval_mi"] = (a0: any) =>
		(_interval_mi = Module["_interval_mi"] = wasmExports["interval_mi"])(a0));
	let _quote_ident = (Module["_quote_ident"] = (a0: any) =>
		(_quote_ident = Module["_quote_ident"] = wasmExports["quote_ident"])(a0));
	let _pg_wchar2mb_with_len = (Module["_pg_wchar2mb_with_len"] = (a0: any, a1: any, a2: any) =>
		(_pg_wchar2mb_with_len = Module["_pg_wchar2mb_with_len"] =
			wasmExports["pg_wchar2mb_with_len"])(a0, a1, a2));
	let _pg_get_indexdef_columns_extended = (Module[
		"_pg_get_indexdef_columns_extended"
	] = (a0: any, a1: any) =>
		(_pg_get_indexdef_columns_extended = Module[
			"_pg_get_indexdef_columns_extended"
		] =
			wasmExports["pg_get_indexdef_columns_extended"])(a0, a1));
	let _pg_get_querydef = (Module["_pg_get_querydef"] = (a0: any, a1: any) =>
		(_pg_get_querydef = Module["_pg_get_querydef"] =
			wasmExports["pg_get_querydef"])(a0, a1));
	_strcspn = (Module["_strcspn"] = (a0: any, a1: any) =>
		(_strcspn = Module["_strcspn"] = wasmExports["strcspn"])(a0, a1));
	let _generic_restriction_selectivity = (Module[
		"_generic_restriction_selectivity"
	] = (a0: any, a1: any, a2: any, a3: any, a4: any, a5: any) =>
		(_generic_restriction_selectivity = Module[
			"_generic_restriction_selectivity"
		] =
			wasmExports["generic_restriction_selectivity"])(a0, a1, a2, a3, a4, a5));
	_genericcostestimate = (Module["_genericcostestimate"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_genericcostestimate = Module["_genericcostestimate"] =
			wasmExports["genericcostestimate"])(a0, a1, a2, a3));
	_tidin = (Module["_tidin"] = (a0: any) =>
		(_tidin = Module["_tidin"] = wasmExports["tidin"])(a0));
	_tidout = (Module["_tidout"] = (a0: any) =>
		(_tidout = Module["_tidout"] = wasmExports["tidout"])(a0));
	let _timestamp_in = (Module["_timestamp_in"] = (a0: any) =>
		(_timestamp_in = Module["_timestamp_in"] = wasmExports["timestamp_in"])(
			a0,
		));
	let _timestamp_eq = (Module["_timestamp_eq"] = (a0: any) =>
		(_timestamp_eq = Module["_timestamp_eq"] = wasmExports["timestamp_eq"])(
			a0,
		));
	let _timestamp_lt = (Module["_timestamp_lt"] = (a0: any) =>
		(_timestamp_lt = Module["_timestamp_lt"] = wasmExports["timestamp_lt"])(
			a0,
		));
	let _timestamp_gt = (Module["_timestamp_gt"] = (a0: any) =>
		(_timestamp_gt = Module["_timestamp_gt"] = wasmExports["timestamp_gt"])(
			a0,
		));
	let _timestamp_le = (Module["_timestamp_le"] = (a0: any) =>
		(_timestamp_le = Module["_timestamp_le"] = wasmExports["timestamp_le"])(
			a0,
		));
	let _timestamp_ge = (Module["_timestamp_ge"] = (a0: any) =>
		(_timestamp_ge = Module["_timestamp_ge"] = wasmExports["timestamp_ge"])(
			a0,
		));
	let _interval_eq = (Module["_interval_eq"] = (a0: any) =>
		(_interval_eq = Module["_interval_eq"] = wasmExports["interval_eq"])(a0));
	let _interval_lt = (Module["_interval_lt"] = (a0: any) =>
		(_interval_lt = Module["_interval_lt"] = wasmExports["interval_lt"])(a0));
	let _interval_gt = (Module["_interval_gt"] = (a0: any) =>
		(_interval_gt = Module["_interval_gt"] = wasmExports["interval_gt"])(a0));
	let _interval_le = (Module["_interval_le"] = (a0: any) =>
		(_interval_le = Module["_interval_le"] = wasmExports["interval_le"])(a0));
	let _interval_ge = (Module["_interval_ge"] = (a0: any) =>
		(_interval_ge = Module["_interval_ge"] = wasmExports["interval_ge"])(a0));
	let _interval_cmp = (Module["_interval_cmp"] = (a0: any) =>
		(_interval_cmp = Module["_interval_cmp"] = wasmExports["interval_cmp"])(
			a0,
		));
	let _timestamp_mi = (Module["_timestamp_mi"] = (a0: any) =>
		(_timestamp_mi = Module["_timestamp_mi"] = wasmExports["timestamp_mi"])(
			a0,
		));
	let _interval_um = (Module["_interval_um"] = (a0: any) =>
		(_interval_um = Module["_interval_um"] = wasmExports["interval_um"])(a0));
	let _has_fn_opclass_options = (Module["_has_fn_opclass_options"] = (a0: any) =>
		(_has_fn_opclass_options = Module["_has_fn_opclass_options"] =
			wasmExports["has_fn_opclass_options"])(a0));
	let _uuid_in = (Module["_uuid_in"] = (a0: any) =>
		(_uuid_in = Module["_uuid_in"] = wasmExports["uuid_in"])(a0));
	let _uuid_out = (Module["_uuid_out"] = (a0: any) =>
		(_uuid_out = Module["_uuid_out"] = wasmExports["uuid_out"])(a0));
	let _uuid_cmp = (Module["_uuid_cmp"] = (a0: any) =>
		(_uuid_cmp = Module["_uuid_cmp"] = wasmExports["uuid_cmp"])(a0));
	let _gen_random_uuid = (Module["_gen_random_uuid"] = (a0: any) =>
		(_gen_random_uuid = Module["_gen_random_uuid"] =
			wasmExports["gen_random_uuid"])(a0));
	let _varbit_in = (Module["_varbit_in"] = (a0: any) =>
		(_varbit_in = Module["_varbit_in"] = wasmExports["varbit_in"])(a0));
	_biteq = (Module["_biteq"] = (a0: any) =>
		(_biteq = Module["_biteq"] = wasmExports["biteq"])(a0));
	_bitlt = (Module["_bitlt"] = (a0: any) =>
		(_bitlt = Module["_bitlt"] = wasmExports["bitlt"])(a0));
	_bitle = (Module["_bitle"] = (a0: any) =>
		(_bitle = Module["_bitle"] = wasmExports["bitle"])(a0));
	_bitgt = (Module["_bitgt"] = (a0: any) =>
		(_bitgt = Module["_bitgt"] = wasmExports["bitgt"])(a0));
	_bitge = (Module["_bitge"] = (a0: any) =>
		(_bitge = Module["_bitge"] = wasmExports["bitge"])(a0));
	_bitcmp = (Module["_bitcmp"] = (a0: any) =>
		(_bitcmp = Module["_bitcmp"] = wasmExports["bitcmp"])(a0));
	_bpchareq = (Module["_bpchareq"] = (a0: any) =>
		(_bpchareq = Module["_bpchareq"] = wasmExports["bpchareq"])(a0));
	_bpcharlt = (Module["_bpcharlt"] = (a0: any) =>
		(_bpcharlt = Module["_bpcharlt"] = wasmExports["bpcharlt"])(a0));
	_bpcharle = (Module["_bpcharle"] = (a0: any) =>
		(_bpcharle = Module["_bpcharle"] = wasmExports["bpcharle"])(a0));
	_bpchargt = (Module["_bpchargt"] = (a0: any) =>
		(_bpchargt = Module["_bpchargt"] = wasmExports["bpchargt"])(a0));
	_bpcharge = (Module["_bpcharge"] = (a0: any) =>
		(_bpcharge = Module["_bpcharge"] = wasmExports["bpcharge"])(a0));
	_bpcharcmp = (Module["_bpcharcmp"] = (a0: any) =>
		(_bpcharcmp = Module["_bpcharcmp"] = wasmExports["bpcharcmp"])(a0));
	_texteq = (Module["_texteq"] = (a0: any) =>
		(_texteq = Module["_texteq"] = wasmExports["texteq"])(a0));
	let _text_lt = (Module["_text_lt"] = (a0: any) =>
		(_text_lt = Module["_text_lt"] = wasmExports["text_lt"])(a0));
	let _text_le = (Module["_text_le"] = (a0: any) =>
		(_text_le = Module["_text_le"] = wasmExports["text_le"])(a0));
	let _text_gt = (Module["_text_gt"] = (a0: any) =>
		(_text_gt = Module["_text_gt"] = wasmExports["text_gt"])(a0));
	let _text_ge = (Module["_text_ge"] = (a0: any) =>
		(_text_ge = Module["_text_ge"] = wasmExports["text_ge"])(a0));
	_bttextcmp = (Module["_bttextcmp"] = (a0: any) =>
		(_bttextcmp = Module["_bttextcmp"] = wasmExports["bttextcmp"])(a0));
	_byteaeq = (Module["_byteaeq"] = (a0: any) =>
		(_byteaeq = Module["_byteaeq"] = wasmExports["byteaeq"])(a0));
	_bytealt = (Module["_bytealt"] = (a0: any) =>
		(_bytealt = Module["_bytealt"] = wasmExports["bytealt"])(a0));
	_byteale = (Module["_byteale"] = (a0: any) =>
		(_byteale = Module["_byteale"] = wasmExports["byteale"])(a0));
	_byteagt = (Module["_byteagt"] = (a0: any) =>
		(_byteagt = Module["_byteagt"] = wasmExports["byteagt"])(a0));
	_byteage = (Module["_byteage"] = (a0: any) =>
		(_byteage = Module["_byteage"] = wasmExports["byteage"])(a0));
	_byteacmp = (Module["_byteacmp"] = (a0: any) =>
		(_byteacmp = Module["_byteacmp"] = wasmExports["byteacmp"])(a0));
	let _to_hex32 = (Module["_to_hex32"] = (a0: any) =>
		(_to_hex32 = Module["_to_hex32"] = wasmExports["to_hex32"])(a0));
	let _varstr_levenshtein = (Module["_varstr_levenshtein"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
	) =>
		(_varstr_levenshtein = Module["_varstr_levenshtein"] =
			wasmExports["varstr_levenshtein"])(a0, a1, a2, a3, a4, a5, a6, a7));
	let _pg_xml_init = (Module["_pg_xml_init"] = (a0: any) =>
		(_pg_xml_init = Module["_pg_xml_init"] = wasmExports["pg_xml_init"])(a0));
	_xmlInitParser = (Module["_xmlInitParser"] = () =>
		(_xmlInitParser = Module["_xmlInitParser"] =
			wasmExports["xmlInitParser"])());
	let _xml_ereport = (Module["_xml_ereport"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_xml_ereport = Module["_xml_ereport"] = wasmExports["xml_ereport"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _pg_xml_done = (Module["_pg_xml_done"] = (a0: any, a1: any) =>
		(_pg_xml_done = Module["_pg_xml_done"] = wasmExports["pg_xml_done"])(
			a0,
			a1,
		));
	_xmlXPathNewContext = (Module["_xmlXPathNewContext"] = (a0: any) =>
		(_xmlXPathNewContext = Module["_xmlXPathNewContext"] =
			wasmExports["xmlXPathNewContext"])(a0));
	_xmlXPathFreeContext = (Module["_xmlXPathFreeContext"] = (a0: any) =>
		(_xmlXPathFreeContext = Module["_xmlXPathFreeContext"] =
			wasmExports["xmlXPathFreeContext"])(a0));
	_xmlFreeDoc = (Module["_xmlFreeDoc"] = (a0: any) =>
		(_xmlFreeDoc = Module["_xmlFreeDoc"] = wasmExports["xmlFreeDoc"])(a0));
	_xmlXPathCtxtCompile = (Module["_xmlXPathCtxtCompile"] = (a0: any, a1: any) =>
		(_xmlXPathCtxtCompile = Module["_xmlXPathCtxtCompile"] =
			wasmExports["xmlXPathCtxtCompile"])(a0, a1));
	_xmlXPathCompiledEval = (Module["_xmlXPathCompiledEval"] = (a0: any, a1: any) =>
		(_xmlXPathCompiledEval = Module["_xmlXPathCompiledEval"] =
			wasmExports["xmlXPathCompiledEval"])(a0, a1));
	_xmlXPathFreeObject = (Module["_xmlXPathFreeObject"] = (a0: any) =>
		(_xmlXPathFreeObject = Module["_xmlXPathFreeObject"] =
			wasmExports["xmlXPathFreeObject"])(a0));
	_xmlXPathFreeCompExpr = (Module["_xmlXPathFreeCompExpr"] = (a0: any) =>
		(_xmlXPathFreeCompExpr = Module["_xmlXPathFreeCompExpr"] =
			wasmExports["xmlXPathFreeCompExpr"])(a0));
	let _pg_do_encoding_conversion = (Module["_pg_do_encoding_conversion"] = (
		a0,
		a1,
		a2,
		a3,
	) =>
		(_pg_do_encoding_conversion = Module["_pg_do_encoding_conversion"] =
			wasmExports["pg_do_encoding_conversion"])(a0, a1, a2, a3));
	_xmlStrdup = (Module["_xmlStrdup"] = (a0: any) =>
		(_xmlStrdup = Module["_xmlStrdup"] = wasmExports["xmlStrdup"])(a0));
	_xmlEncodeSpecialChars = (Module["_xmlEncodeSpecialChars"] = (a0: any, a1: any) =>
		(_xmlEncodeSpecialChars = Module["_xmlEncodeSpecialChars"] =
			wasmExports["xmlEncodeSpecialChars"])(a0, a1));
	_xmlStrlen = (Module["_xmlStrlen"] = (a0: any) =>
		(_xmlStrlen = Module["_xmlStrlen"] = wasmExports["xmlStrlen"])(a0));
	_xmlBufferCreate = (Module["_xmlBufferCreate"] = () =>
		(_xmlBufferCreate = Module["_xmlBufferCreate"] =
			wasmExports["xmlBufferCreate"])());
	_xmlBufferFree = (Module["_xmlBufferFree"] = (a0: any) =>
		(_xmlBufferFree = Module["_xmlBufferFree"] = wasmExports["xmlBufferFree"])(
			a0,
		));
	_xmlXPathCastNodeToString = (Module["_xmlXPathCastNodeToString"] = (a0: any) =>
		(_xmlXPathCastNodeToString = Module["_xmlXPathCastNodeToString"] =
			wasmExports["xmlXPathCastNodeToString"])(a0));
	_xmlNodeDump = (Module["_xmlNodeDump"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_xmlNodeDump = Module["_xmlNodeDump"] = wasmExports["xmlNodeDump"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	let _get_typsubscript = (Module["_get_typsubscript"] = (a0: any, a1: any) =>
		(_get_typsubscript = Module["_get_typsubscript"] =
			wasmExports["get_typsubscript"])(a0, a1));
	_CachedPlanAllowsSimpleValidityCheck = (Module[
		"_CachedPlanAllowsSimpleValidityCheck"
	] = (a0: any, a1: any, a2: any) =>
		(_CachedPlanAllowsSimpleValidityCheck = Module[
			"_CachedPlanAllowsSimpleValidityCheck"
		] =
			wasmExports["CachedPlanAllowsSimpleValidityCheck"])(a0, a1, a2));
	_CachedPlanIsSimplyValid = (Module["_CachedPlanIsSimplyValid"] = (
		a0,
		a1,
		a2,
	) =>
		(_CachedPlanIsSimplyValid = Module["_CachedPlanIsSimplyValid"] =
			wasmExports["CachedPlanIsSimplyValid"])(a0, a1, a2));
	_GetCachedExpression = (Module["_GetCachedExpression"] = (a0: any) =>
		(_GetCachedExpression = Module["_GetCachedExpression"] =
			wasmExports["GetCachedExpression"])(a0));
	_FreeCachedExpression = (Module["_FreeCachedExpression"] = (a0: any) =>
		(_FreeCachedExpression = Module["_FreeCachedExpression"] =
			wasmExports["FreeCachedExpression"])(a0));
	_ReleaseAllPlanCacheRefsInOwner = (Module[
		"_ReleaseAllPlanCacheRefsInOwner"
	] = (a0: any) =>
		(_ReleaseAllPlanCacheRefsInOwner = Module[
			"_ReleaseAllPlanCacheRefsInOwner"
		] =
			wasmExports["ReleaseAllPlanCacheRefsInOwner"])(a0));
	let _in_error_recursion_trouble = (Module["_in_error_recursion_trouble"] =
		() =>
			(_in_error_recursion_trouble = Module["_in_error_recursion_trouble"] =
				wasmExports["in_error_recursion_trouble"])());
	_GetErrorContextStack = (Module["_GetErrorContextStack"] = () =>
		(_GetErrorContextStack = Module["_GetErrorContextStack"] =
			wasmExports["GetErrorContextStack"])());
	let _find_rendezvous_variable = (Module["_find_rendezvous_variable"] = (a0: any) =>
		(_find_rendezvous_variable = Module["_find_rendezvous_variable"] =
			wasmExports["find_rendezvous_variable"])(a0));
	_CallerFInfoFunctionCall2 = (Module["_CallerFInfoFunctionCall2"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
	) =>
		(_CallerFInfoFunctionCall2 = Module["_CallerFInfoFunctionCall2"] =
			wasmExports["CallerFInfoFunctionCall2"])(a0, a1, a2, a3, a4));
	_FunctionCall0Coll = (Module["_FunctionCall0Coll"] = (a0: any, a1: any) =>
		(_FunctionCall0Coll = Module["_FunctionCall0Coll"] =
			wasmExports["FunctionCall0Coll"])(a0, a1));
	let _resolve_polymorphic_argtypes = (Module["_resolve_polymorphic_argtypes"] =
		(a0: any, a1: any, a2: any, a3: any) =>
			(_resolve_polymorphic_argtypes = Module["_resolve_polymorphic_argtypes"] =
				wasmExports["resolve_polymorphic_argtypes"])(a0, a1, a2, a3));
	let _pg_bindtextdomain = (Module["_pg_bindtextdomain"] = (a0: any) =>
		(_pg_bindtextdomain = Module["_pg_bindtextdomain"] =
			wasmExports["pg_bindtextdomain"])(a0));
	_DefineCustomBoolVariable = (Module["_DefineCustomBoolVariable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
	) =>
		(_DefineCustomBoolVariable = Module["_DefineCustomBoolVariable"] =
			wasmExports["DefineCustomBoolVariable"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
		));
	_DefineCustomIntVariable = (Module["_DefineCustomIntVariable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
		a11,
	) =>
		(_DefineCustomIntVariable = Module["_DefineCustomIntVariable"] =
			wasmExports["DefineCustomIntVariable"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
			a11,
		));
	_DefineCustomRealVariable = (Module["_DefineCustomRealVariable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
		a11,
	) =>
		(_DefineCustomRealVariable = Module["_DefineCustomRealVariable"] =
			wasmExports["DefineCustomRealVariable"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
			a11,
		));
	_DefineCustomStringVariable = (Module["_DefineCustomStringVariable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
	) =>
		(_DefineCustomStringVariable = Module["_DefineCustomStringVariable"] =
			wasmExports["DefineCustomStringVariable"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
		));
	_DefineCustomEnumVariable = (Module["_DefineCustomEnumVariable"] = (
		a0,
		a1,
		a2,
		a3,
		a4,
		a5,
		a6,
		a7,
		a8,
		a9,
		a10,
	) =>
		(_DefineCustomEnumVariable = Module["_DefineCustomEnumVariable"] =
			wasmExports["DefineCustomEnumVariable"])(
			a0,
			a1,
			a2,
			a3,
			a4,
			a5,
			a6,
			a7,
			a8,
			a9,
			a10,
		));
	_MarkGUCPrefixReserved = (Module["_MarkGUCPrefixReserved"] = (a0: any) =>
		(_MarkGUCPrefixReserved = Module["_MarkGUCPrefixReserved"] =
			wasmExports["MarkGUCPrefixReserved"])(a0));
	let _sampler_random_init_state = (Module["_sampler_random_init_state"] = (
		a0,
		a1,
	) =>
		(_sampler_random_init_state = Module["_sampler_random_init_state"] =
			wasmExports["sampler_random_init_state"])(a0, a1));
	_pchomp = (Module["_pchomp"] = (a0: any) =>
		(_pchomp = Module["_pchomp"] = wasmExports["pchomp"])(a0));
	_PinPortal = (Module["_PinPortal"] = (a0: any) =>
		(_PinPortal = Module["_PinPortal"] = wasmExports["PinPortal"])(a0));
	_UnpinPortal = (Module["_UnpinPortal"] = (a0: any) =>
		(_UnpinPortal = Module["_UnpinPortal"] = wasmExports["UnpinPortal"])(a0));
	_xmlBufferWriteCHAR = (Module["_xmlBufferWriteCHAR"] = (a0: any, a1: any) =>
		(_xmlBufferWriteCHAR = Module["_xmlBufferWriteCHAR"] =
			wasmExports["xmlBufferWriteCHAR"])(a0, a1));
	_xmlBufferWriteChar = (Module["_xmlBufferWriteChar"] = (a0: any, a1: any) =>
		(_xmlBufferWriteChar = Module["_xmlBufferWriteChar"] =
			wasmExports["xmlBufferWriteChar"])(a0, a1));
	_xmlReadMemory = (Module["_xmlReadMemory"] = (a0: any, a1: any, a2: any, a3: any, a4: any) =>
		(_xmlReadMemory = Module["_xmlReadMemory"] = wasmExports["xmlReadMemory"])(
			a0,
			a1,
			a2,
			a3,
			a4,
		));
	_xmlDocGetRootElement = (Module["_xmlDocGetRootElement"] = (a0: any) =>
		(_xmlDocGetRootElement = Module["_xmlDocGetRootElement"] =
			wasmExports["xmlDocGetRootElement"])(a0));
	_xmlXPathIsNaN = (Module["_xmlXPathIsNaN"] = (a0: any) =>
		(_xmlXPathIsNaN = Module["_xmlXPathIsNaN"] = wasmExports["xmlXPathIsNaN"])(
			a0,
		));
	_xmlXPathCastToBoolean = (Module["_xmlXPathCastToBoolean"] = (a0: any) =>
		(_xmlXPathCastToBoolean = Module["_xmlXPathCastToBoolean"] =
			wasmExports["xmlXPathCastToBoolean"])(a0));
	_xmlXPathCastToNumber = (Module["_xmlXPathCastToNumber"] = (a0: any) =>
		(_xmlXPathCastToNumber = Module["_xmlXPathCastToNumber"] =
			wasmExports["xmlXPathCastToNumber"])(a0));
	let _deflateInit_ = (Module["_deflateInit_"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_deflateInit_ = Module["_deflateInit_"] = wasmExports["deflateInit_"])(
			a0,
			a1,
			a2,
			a3,
		));
	_deflateEnd = (Module["_deflateEnd"] = (a0: any) =>
		(_deflateEnd = Module["_deflateEnd"] = wasmExports["deflateEnd"])(a0));
	let _inflateInit2_ = (Module["_inflateInit2_"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_inflateInit2_ = Module["_inflateInit2_"] = wasmExports["inflateInit2_"])(
			a0,
			a1,
			a2,
			a3,
		));
	let _inflateInit_ = (Module["_inflateInit_"] = (a0: any, a1: any, a2: any) =>
		(_inflateInit_ = Module["_inflateInit_"] = wasmExports["inflateInit_"])(
			a0,
			a1,
			a2,
		));
	_inflate = (Module["_inflate"] = (a0: any, a1: any) =>
		(_inflate = Module["_inflate"] = wasmExports["inflate"])(a0, a1));
	_inflateEnd = (Module["_inflateEnd"] = (a0: any) =>
		(_inflateEnd = Module["_inflateEnd"] = wasmExports["inflateEnd"])(a0));
	let ___dl_seterr = (a0: any, a1: any) =>
		(___dl_seterr = wasmExports["__dl_seterr"])(a0, a1);
	_getgid = (Module["_getgid"] = () =>
		(_getgid = Module["_getgid"] = wasmExports["getgid"])());
	_getuid = (Module["_getuid"] = () =>
		(_getuid = Module["_getuid"] = wasmExports["getuid"])());
	_gmtime = (Module["_gmtime"] = (a0: any) =>
		(_gmtime = Module["_gmtime"] = wasmExports["gmtime"])(a0));
	_htonl = (a0: any) => (_htonl = wasmExports["htonl"])(a0);
	_htons = (a0: any) => (_htons = wasmExports["htons"])(a0);
	_ioctl = (Module["_ioctl"] = (a0: any, a1: any, a2: any) =>
		(_ioctl = Module["_ioctl"] = wasmExports["ioctl"])(a0, a1, a2));
	let _emscripten_builtin_memalign = (a0: any, a1: any) =>
		(_emscripten_builtin_memalign = wasmExports["emscripten_builtin_memalign"])(
			a0,
			a1,
		);
	_ntohs = (a0: any) => (_ntohs = wasmExports["ntohs"])(a0);
	_perror = (Module["_perror"] = (a0: any) =>
		(_perror = Module["_perror"] = wasmExports["perror"])(a0));
	_qsort = (Module["_qsort"] = (a0: any, a1: any, a2: any, a3: any) =>
		(_qsort = Module["_qsort"] = wasmExports["qsort"])(a0, a1, a2, a3));
	_srand = (Module["_srand"] = (a0: any) =>
		(_srand = Module["_srand"] = wasmExports["srand"])(a0));
	_rand = (Module["_rand"] = () =>
		(_rand = Module["_rand"] = wasmExports["rand"])());
	let __emscripten_timeout = (a0: any, a1: any) =>
		(__emscripten_timeout = wasmExports["_emscripten_timeout"])(a0, a1);
	let _strerror_r = (Module["_strerror_r"] = (a0: any, a1: any, a2: any) =>
		(_strerror_r = Module["_strerror_r"] = wasmExports["strerror_r"])(
			a0,
			a1,
			a2,
		));
	_strncat = (Module["_strncat"] = (a0: any, a1: any, a2: any) =>
		(_strncat = Module["_strncat"] = wasmExports["strncat"])(a0, a1, a2));
	_setThrew = (a0: any, a1: any) => (_setThrew = wasmExports["setThrew"])(a0, a1);
	let __emscripten_tempret_set = (a0: any) =>
		(__emscripten_tempret_set = wasmExports["_emscripten_tempret_set"])(a0);
	let __emscripten_tempret_get = () =>
		(__emscripten_tempret_get = wasmExports["_emscripten_tempret_get"])();
	let __emscripten_stack_restore = (a0: any) =>
		(__emscripten_stack_restore = wasmExports["_emscripten_stack_restore"])(a0);
	let __emscripten_stack_alloc = (a0: any) =>
		(__emscripten_stack_alloc = wasmExports["_emscripten_stack_alloc"])(a0);
	let _emscripten_stack_get_current = () =>
		(_emscripten_stack_get_current =
			wasmExports["emscripten_stack_get_current"])();
	let ___wasm_apply_data_relocs = () =>
		(___wasm_apply_data_relocs = wasmExports["__wasm_apply_data_relocs"])();
	Module["_stderr"] = 2539328;
	Module["_InterruptPending"] = 2680352;
	Module["_MyLatch"] = 2680540;
	Module["_CritSectionCount"] = 2680404;
	Module["_MyProc"] = 2650156;
	Module["_pg_global_prng_state"] = 2626736;
	Module["_error_context_stack"] = 2678648;
	Module["_GUC_check_errdetail_string"] = 2684300;
	Module["_IsUnderPostmaster"] = 2680433;
	Module["_CurrentMemoryContext"] = 2685728;
	Module["_stdout"] = 2539632;
	Module["_debug_query_string"] = 2541180;
	Module["_MyProcPort"] = 2680528;
	Module["___THREW__"] = 2701396;
	Module["___threwValue"] = 2701400;
	Module["_MyDatabaseId"] = 2680412;
	Module["_TopMemoryContext"] = 2685732;
	Module["_PG_exception_stack"] = 2678652;
	Module["_MyProcPid"] = 2680504;
	Module["_stdin"] = 2539480;
	Module["_ScanKeywords"] = 2376520;
	Module["_pg_number_of_ones"] = 925120;
	Module["_LocalBufferBlockPointers"] = 2646732;
	Module["_BufferBlocks"] = 2641468;
	Module["_wal_level"] = 2390400;
	Module["_SnapshotAnyData"] = 2476576;
	Module["_maintenance_work_mem"] = 2424056;
	Module["_ParallelWorkerNumber"] = 2381960;
	Module["_MainLWLockArray"] = 2648340;
	Module["_CurrentResourceOwner"] = 2685776;
	Module["_work_mem"] = 2424040;
	Module["_NBuffers"] = 2424064;
	Module["_bsysscan"] = 2627972;
	Module["_CheckXidAlive"] = 2627968;
	Module["_RecentXmin"] = 2476668;
	Module["_XactIsoLevel"] = 2390264;
	Module["_pgWalUsage"] = 2631440;
	Module["_pgBufferUsage"] = 2631312;
	Module["_TTSOpsVirtual"] = 2394088;
	Module["_TransamVariables"] = 2627960;
	Module["_TopTransactionContext"] = 2685752;
	Module["_RmgrTable"] = 2381984;
	Module["_process_shared_preload_libraries_in_progress"] = 2683696;
	Module["_wal_segment_size"] = 2390420;
	Module["_TopTransactionResourceOwner"] = 2685784;
	Module["_arch_module_check_errdetail_string"] = 2640852;
	Module["_object_access_hook"] = 2630080;
	Module["_InvalidObjectAddress"] = 1520620;
	Module["_check_function_bodies"] = 2424230;
	Module["_post_parse_analyze_hook"] = 2630120;
	Module["_ScanKeywordTokens"] = 1551648;
	Module["_SPI_processed"] = 2631464;
	Module["_SPI_tuptable"] = 2631472;
	Module["_TTSOpsMinimalTuple"] = 2394192;
	Module["_check_password_hook"] = 2630388;
	Module["_ConfigReloadPending"] = 2640840;
	Module["_max_parallel_maintenance_workers"] = 2424060;
	Module["_DateStyle"] = 2424028;
	Module["_ExecutorStart_hook"] = 2631288;
	Module["_ExecutorRun_hook"] = 2631292;
	Module["_ExecutorFinish_hook"] = 2631296;
	Module["_ExecutorEnd_hook"] = 2631300;
	Module["_SPI_result"] = 2631476;
	Module["_ClientAuthentication_hook"] = 2631648;
	Module["_cpu_tuple_cost"] = 2394648;
	Module["_cpu_operator_cost"] = 2394664;
	Module["_seq_page_cost"] = 2394632;
	Module["_planner_hook"] = 2640536;
	Module["_ShutdownRequestPending"] = 2640844;
	Module["_MyStartTime"] = 2680512;
	Module["_cluster_name"] = 2424280;
	Module["_application_name"] = 2684524;
	Module["_BufferDescriptors"] = 2641464;
	Module["_shmem_startup_hook"] = 2647412;
	Module["_ProcessUtility_hook"] = 2650244;
	Module["_IntervalStyle"] = 2680436;
	Module["_extra_float_digits"] = 2414456;
	Module["_pg_crc32_table"] = 2112288;
	Module["_xmlFree"] = 2525880;
	Module["_shmem_request_hook"] = 2683700;
	Module["addRunDependency"] = addRunDependency;
	Module["removeRunDependency"] = removeRunDependency;
	Module["wasmTable"] = wasmTable;
	Module["addFunction"] = addFunction;
	Module["removeFunction"] = removeFunction;
	Module["setValue"] = setValue;
	Module["getValue"] = getValue;
	Module["UTF8ToString"] = UTF8ToString;
	Module["stringToNewUTF8"] = stringToNewUTF8;
	Module["stringToUTF8OnStack"] = stringToUTF8OnStack;
	Module["FS_createPreloadedFile"] = FS_createPreloadedFile;
	Module["FS_unlink"] = FS_unlink;
	Module["FS_createPath"] = FS_createPath;
	Module["FS_createDevice"] = FS_createDevice;
	Module["FS"] = FS;
	Module["FS_createDataFile"] = FS_createDataFile;
	Module["FS_createLazyFile"] = FS_createLazyFile;
	Module["MEMFS"] = MEMFS;
	Module["IDBFS"] = IDBFS;

	// 7) Program run loop
	let calledRun: boolean | undefined;
	dependenciesFulfilled = function runCaller() {
		if (!calledRun) {
			run();
		}
		if (!calledRun) {
			dependenciesFulfilled = runCaller;
		}
	};
	function callMain(args: string[] = []) {
		const entryFunction = resolveGlobalSymbol("main").sym;
		if (!entryFunction) {
			return;
		}
		args.unshift(thisProgram);
		const argc = args.length;
		const argv = stackAlloc((argc + 1) * 4);
		let argv_ptr = argv;
		args.forEach((arg: any) => {
			HEAPU32[argv_ptr >> 2] = stringToUTF8OnStack(arg);
			argv_ptr += 4;
		});
		HEAPU32[argv_ptr >> 2] = 0;
		try {
			const ret = entryFunction(argc, argv);
			_exit(ret, true);
			return ret;
		} catch (e) {
			return handleException(e);
		}
	}
	function run(args: any = arguments_) {
		if (runDependencies > 0) {
			return;
		}
		preRun();
		if (runDependencies > 0) {
			return;
		}
		function doRun() {
			if (calledRun) {
				return;
			}
			calledRun = true;
			Module["calledRun"] = true;
			if (ABORT) {
				return;
			}
			initRuntime();
			preMain();
			readyPromiseResolve(Module);
			Module["onRuntimeInitialized"]?.();
			if (shouldRunNow) {
				callMain(args);
			}
			postRun();
		}
		if (Module["setStatus"]) {
			Module["setStatus"]("Running...");
			setTimeout(() => {
				setTimeout(() => Module["setStatus"](""), 1);
				doRun();
			}, 1);
		} else {
			doRun();
		}
	}
	if (Module["preInit"]) {
		if (typeof Module["preInit"] == "function") {
			Module["preInit"] = [Module["preInit"]];
		}
		while (Module["preInit"].length > 0) {
			Module["preInit"].pop()();
		}
	}
	let shouldRunNow = true;
	if (Module["noInitialRun"]) {
		shouldRunNow = false;
	}
	run();
	return readyPromise;
}
