export const createInvokeWrappers = ({
  stackSave,
  stackRestore,
  getWasmTableEntry,
  _setThrew,
}: {
  stackSave: () => number;
  stackRestore: (sp: number) => void;
  getWasmTableEntry: (funcPtr: number) => (...args: any[]) => any;
  _setThrew: (threw: number, value: number) => void;
}) => {
function invoke_iii(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiii(index: any, a1: any, a2: any, a3: any, a4: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vi(index: any, a1: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_v(index: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_j(index: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_viiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vii(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_i(index: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ii(index: any, a1: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viii(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vji(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiii(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiii(index: any, a1: any, a2: any, a3: any, a4: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_jii(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_ji(index: any, a1: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_jiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_jiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_iiiiiiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any, a10: any, a11: any, a12: any, a13: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiijii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vijiji(index: any, a1: any, a2: any, a3: any, a4: any, a5: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viji(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiji(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiiiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any, a10: any, a11: any, a12: any, a13: any, a14: any, a15: any, a16: any, a17: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiij(index: any, a1: any, a2: any, a3: any, a4: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vj(index: any, a1: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiji(index: any, a1: any, a2: any, a3: any, a4: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vij(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ij(index: any, a1: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiji(index: any, a1: any, a2: any, a3: any, a4: any, a5: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiij(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vid(index: any, a1: any, a2: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ijiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viijii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiji(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viijiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viij(index: any, a1: any, a2: any, a3: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_jiiii(index: any, a1: any, a2: any, a3: any, a4: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_viiiiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any, a10: any, a11: any, a12: any) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_di(index: any, a1: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_id(index: any, a1: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ijiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiiii(index: any, a1: any, a2: any, a3: any, a4: any, a5: any, a6: any, a7: any, a8: any, a9: any, a10: any) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      
return { invoke_iii, invoke_viiii, invoke_vi, invoke_v, invoke_j, invoke_viiiiii, invoke_vii, invoke_iiiiii, invoke_i, invoke_ii, invoke_viii, invoke_vji, invoke_iiii, invoke_iiiiiiii, invoke_iiiii, invoke_viiiiiiiii, invoke_viiiii, invoke_jii, invoke_ji, invoke_jiiiiiiiii, invoke_jiiiiii, invoke_iiiiiiiiiiiiii, invoke_iiiijii, invoke_vijiji, invoke_viji, invoke_iiji, invoke_iiiiiiiii, invoke_iiiiiiiiiiiiiiiiii, invoke_iiiij, invoke_iiiiiii, invoke_vj, invoke_iiiiiiiiii, invoke_viiji, invoke_viiiiiiii, invoke_vij, invoke_ij, invoke_viiiiiii, invoke_viiiji, invoke_iiij, invoke_vid, invoke_ijiiiiii, invoke_viijii, invoke_iiiiiji, invoke_viijiiii, invoke_viij, invoke_jiiii, invoke_viiiiiiiiiiii, invoke_di, invoke_id, invoke_ijiiiii, invoke_iiiiiiiiiii };
};
