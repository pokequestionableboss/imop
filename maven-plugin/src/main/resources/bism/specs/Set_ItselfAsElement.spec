pointcut pc1 before MethodCall(* *.*Set.addAll(Collection))
pointcut pc2 before MethodCall(* *.*Set.add(Object))

event e1("Set_ItselfAsElement", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Set_ItselfAsElement", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc2 to Monitor.receiveEvents(String, String, List)
