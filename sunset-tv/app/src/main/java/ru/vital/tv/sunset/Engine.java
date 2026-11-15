package ru.vital.tv.sunset;

/** Device-independent transactional controller. Save recovery data before every panel mutation. */
public final class Engine {
    public interface Store { State load(); void save(State s); }
    public interface Panel { void requireReady(); int read(); void write(int value); }
    public interface Alarms { void automatic(long at); void rollback(long at); void cancelAutomatic(); void cancelRollback(); }
    public static final class State {
        public boolean enabled,verified,testSucceeded,restorePending;
        public Integer original,lastApplied,testOriginal,testApplied;
        public long testUntil,nextAt;
        public String message="Автоматика выключена";
        public State copy() {
            State s=new State();s.enabled=enabled;s.verified=verified;s.testSucceeded=testSucceeded;
            s.restorePending=restorePending;s.original=original;s.lastApplied=lastApplied;
            s.testOriginal=testOriginal;s.testApplied=testApplied;s.testUntil=testUntil;s.nextAt=nextAt;s.message=message;
            return s;
        }
    }
    private final Store store; private final Panel panel; private final Alarms alarms;
    public Engine(Store store,Panel panel,Alarms alarms) {this.store=store;this.panel=panel;this.alarms=alarms;}
    public void startTest(Plan plan,long now) {
        State s=store.load();
        if(s.enabled||s.testOriginal!=null||s.restorePending) throw new IllegalStateException("Сначала выключи автоматику и заверши возврат подсветки");
        panel.requireReady();
        int original=panel.read();
        if(original==plan.nightLevel) throw new IllegalStateException("Ночная яркость уже установлена. Выбери другой уровень для заметного теста");
        s.testSucceeded=false;s.testOriginal=original;s.testApplied=plan.nightLevel;s.testUntil=now+10_000;
        s.message="Тест ночной подсветки: возврат через 10 секунд";
        store.save(s); // Durable recovery, even if the process dies on the next line.
        try {
            alarms.rollback(s.testUntil); // Install the return path BEFORE darkening.
            panel.write(s.testApplied);
        } catch(RuntimeException failure) {
            s.testSucceeded=false;s.message="Тест прерван: "+failure.getMessage();store.save(s);
            try {finishTest(false);} catch(RuntimeException ignored) { /* Backup remains for the next recovery. */ }
            throw failure;
        }
    }
    public void confirmTest() {
        State s=store.load();
        if(s.testOriginal!=null||!s.testSucceeded) throw new IllegalStateException("Сначала дождись окончания теста и возврата яркости");
        s.verified=true;s.message="Управление подсветкой подтверждено";store.save(s);
    }
    public void enable(Plan plan,long now) {
        State s=store.load();
        if(!s.verified) throw new IllegalStateException("Сначала проверь ночную яркость и подтверди результат");
        if(s.testOriginal!=null||s.restorePending) throw new IllegalStateException("Сначала дождись возврата прежней подсветки");
        panel.requireReady();
        if(!s.enabled) {
            s.original=panel.read();s.lastApplied=null;s.enabled=true;s.message="Включаю расписание";store.save(s);
        }
        try {tick(plan,now,false);} catch(RuntimeException e) {
            try {disable();} catch(RuntimeException ignored) {}
            throw e;
        }
    }
    public void tick(Plan plan,long now,boolean forceRecovery) {
        State s=store.load();
        if(s.testOriginal!=null) {
            if(forceRecovery||now>=s.testUntil) finishTest(!forceRecovery);
            else {alarms.rollback(s.testUntil);return;}
            s=store.load();
        }
        if(s.restorePending) {disable();s=store.load();}
        if(!s.enabled) {alarms.cancelAutomatic();return;}
        if(!s.verified||s.original==null) {disable();throw new IllegalStateException("Не найдена сохранённая исходная подсветка");}
        panel.requireReady();
        Plan.Now desired=plan.at(now);
        // Record intent before the write so an interrupted transition remains reversible.
        s.lastApplied=desired.level;s.nextAt=desired.nextAt;store.save(s);
        alarms.automatic(desired.nextAt);
        if(panel.read()!=desired.level) panel.write(desired.level);
        s.message=desired.label+" · "+desired.level+"%";store.save(s);
    }
    private void finishTest(boolean successfulTimer) {
        State s=store.load();
        if(s.testOriginal==null) {alarms.cancelRollback();return;}
        panel.requireReady();
        int current=panel.read();
        boolean ours=s.testApplied!=null&&current==s.testApplied;
        if(ours) panel.write(s.testOriginal);
        boolean restored=panel.read()==s.testOriginal;
        s.testSucceeded=successfulTimer&&ours&&restored;
        s.testOriginal=null;s.testApplied=null;s.testUntil=0;
        s.message=s.testSucceeded?"Тест завершён, исходная подсветка возвращена. Подтверди, что экран стал темнее":"Тест завершён; ручное изменение сохранено или тест был прерван";
        store.save(s);alarms.cancelRollback();
    }
    public void disable() {
        State s=store.load();s.enabled=false;s.nextAt=0;store.save(s);alarms.cancelAutomatic();
        if(s.testOriginal!=null) {finishTest(false);s=store.load();}
        if(s.original!=null) {
            s.restorePending=true;store.save(s);
            panel.requireReady();panel.write(s.original);
            s.original=null;s.lastApplied=null;s.restorePending=false;
        }
        s.message="Автоматика выключена, исходная подсветка возвращена";store.save(s);
    }
    public void recordFailure(RuntimeException e) {
        State s=store.load();s.message="Не удалось применить подсветку: "+e.getMessage();store.save(s);
    }
}
