pointcut pc1 before MethodCall(* *.*Appendable.append(..))

event e1("Appendable_ThreadSafe", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
