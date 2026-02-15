export const createInvokeWrappers = ({ stackSave, stackRestore, getWasmTableEntry, _setThrew }) => {
function invoke_iii(index, a1, a2) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiii(index, a1, a2, a3, a4) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vi(index, a1) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_v(index) {
        var sp = stackSave();
        try { getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_j(index) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_viiiiii(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vii(index, a1, a2) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiii(index, a1, a2, a3, a4, a5) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_i(index) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)() } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ii(index, a1) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viii(index, a1, a2, a3) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vji(index, a1, a2) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiii(index, a1, a2, a3) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiii(index, a1, a2, a3, a4, a5, a6, a7) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiii(index, a1, a2, a3, a4) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiii(index, a1, a2, a3, a4, a5) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_jii(index, a1, a2) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_ji(index, a1) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_jiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_jiiiiii(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_iiiiiiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiijii(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vijiji(index, a1, a2, a3, a4, a5) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viji(index, a1, a2, a3) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiji(index, a1, a2, a3) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiiiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiij(index, a1, a2, a3, a4) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiii(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vj(index, a1) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiji(index, a1, a2, a3, a4) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vij(index, a1, a2) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ij(index, a1) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiiiii(index, a1, a2, a3, a4, a5, a6, a7) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viiiji(index, a1, a2, a3, a4, a5) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiij(index, a1, a2, a3) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_vid(index, a1, a2) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ijiiiiii(index, a1, a2, a3, a4, a5, a6, a7) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viijii(index, a1, a2, a3, a4, a5) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiji(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viijiiii(index, a1, a2, a3, a4, a5, a6, a7) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_viij(index, a1, a2, a3) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_jiiii(index, a1, a2, a3, a4) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0);
          return 0n
        }
      }
      function invoke_viiiiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) {
        var sp = stackSave();
        try { getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_di(index, a1) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_id(index, a1) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_ijiiiii(index, a1, a2, a3, a4, a5, a6) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      function invoke_iiiiiiiiiii(index, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) {
        var sp = stackSave();
        try { return getWasmTableEntry(index)(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10) } catch (e) {
          stackRestore(sp);
          if (e !== e + 0) throw e;
          _setThrew(1, 0)
        }
      }
      
return { invoke_iii, invoke_viiii, invoke_vi, invoke_v, invoke_j, invoke_viiiiii, invoke_vii, invoke_iiiiii, invoke_i, invoke_ii, invoke_viii, invoke_vji, invoke_iiii, invoke_iiiiiiii, invoke_iiiii, invoke_viiiiiiiii, invoke_viiiii, invoke_jii, invoke_ji, invoke_jiiiiiiiii, invoke_jiiiiii, invoke_iiiiiiiiiiiiii, invoke_iiiijii, invoke_vijiji, invoke_viji, invoke_iiji, invoke_iiiiiiiii, invoke_iiiiiiiiiiiiiiiiii, invoke_iiiij, invoke_iiiiiii, invoke_vj, invoke_iiiiiiiiii, invoke_viiji, invoke_viiiiiiii, invoke_vij, invoke_ij, invoke_viiiiiii, invoke_viiiji, invoke_iiij, invoke_vid, invoke_ijiiiiii, invoke_viijii, invoke_iiiiiji, invoke_viijiiii, invoke_viij, invoke_jiiii, invoke_viiiiiiiiiiii, invoke_di, invoke_id, invoke_ijiiiii, invoke_iiiiiiiiiii };
};
