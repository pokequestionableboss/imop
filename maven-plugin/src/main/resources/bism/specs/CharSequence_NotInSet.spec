pointcut pc1 before MethodCall(* *.*Set.putAll(Collection))
pointcut pc2 before MethodCall(* *.*Set.add(..))

event e1("CharSequence_NotInSet", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("CharSequence_NotInSet", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc2 to Monitor.receiveEvents(String, String, List)
