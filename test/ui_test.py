#!/usr/bin/env python3
"""Drives the Omooma app on an emulator: taps every screen/button by its Arabic label,
screenshots each step, and records crashes. Results go to results/."""
import subprocess, time, re, os, shutil, xml.etree.ElementTree as ET

PKG = "com.nmmsoft.hanan"
OUT = "results"
shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT + "/shots")
LOG = open(OUT + "/report.md", "w", encoding="utf-8")
n = [0]; fails = []

def sh(cmd, check=False):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return r.stdout
def adb(c): return sh("adb " + c)
def log(s): print(s, flush=True); LOG.write(s + "\n"); LOG.flush()

W, H = 1080, 2400
def dims():
    global W, H
    m = re.search(r"(\d+)x(\d+)", adb("shell wm size"))
    if m: W, H = int(m.group(1)), int(m.group(2))

def dump():
    for _ in range(3):
        adb("shell uiautomator dump /sdcard/ui.xml")
        x = adb("shell cat /sdcard/ui.xml")
        if "<hierarchy" in x:
            try: return ET.fromstring(x[x.index("<?xml") if "<?xml" in x else x.index("<hierarchy"):])
            except Exception: pass
        time.sleep(1)
    return None

def bounds(node):
    m = re.findall(r"\d+", node.get("bounds", ""))
    return tuple(map(int, m)) if len(m) == 4 else None

def find(label, exact=True, cls=None):
    root = dump()
    if root is None: return None
    best = None
    for nd in root.iter("node"):
        t = nd.get("text", ""); d = nd.get("content-desc", "")
        if cls and nd.get("class") != cls: continue
        ok = (t == label or d == label) if exact else (label in t or label in d)
        if ok:
            b = bounds(nd)
            if b and b[3] > b[1] and b[1] >= 0 and b[3] <= H - 10 and b[1] > 60:
                return b
            best = best or b
    return None

def alive(): return adb("shell pidof " + PKG).strip() != ""

def shot(name):
    n[0] += 1
    fn = f"{OUT}/shots/{n[0]:02d}_{name}.png"
    sh(f"adb exec-out screencap -p > {fn}")
    return fn

def error_sheet():
    root = dump()
    if root is None: return False
    txt = " ".join(nd.get("text", "") for nd in root.iter("node"))
    return "حدث خطأ بسيط" in txt or "حدث خلل" in txt or "isn't responding" in txt or "keeps stopping" in txt

def check(step):
    time.sleep(1.2)
    if not alive():
        fails.append(step); log(f"- ❌ **{step}**: APP CLOSED / CRASHED")
        crash = adb("logcat -d -b crash")
        open(f"{OUT}/crash_{len(fails)}.txt", "w").write(crash)
        adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(3)
        return False
    if error_sheet():
        fails.append(step); log(f"- ⚠️ **{step}**: error sheet shown"); shot("error_" + step)
        return False
    return True

def scroll(down=True):
    if down: adb(f"shell input swipe {W//2} {int(H*0.75)} {W//2} {int(H*0.35)} 300")
    else: adb(f"shell input swipe {W//2} {int(H*0.35)} {W//2} {int(H*0.75)} 300")
    time.sleep(0.8)

def tap(label, step=None, exact=True, cls=None, scrolls=5, up_first=False):
    step = step or label
    b = find(label, exact, cls)
    tries = 0
    while b is None and tries < scrolls:
        scroll(down=not up_first); tries += 1; b = find(label, exact, cls)
    if b is None and up_first is False:
        for _ in range(scrolls):
            scroll(down=False); b = find(label, exact, cls)
            if b: break
    if b is None:
        fails.append(step); log(f"- ❌ **{step}**: button/label «{label}» not found on screen"); shot("notfound_" + re.sub(r"\W", "", step)[:20] or "x")
        return False
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    adb(f"shell input tap {x} {y}"); time.sleep(1.5)
    ok = check(step)
    if ok: log(f"- ✅ {step}")
    return ok

def visible_texts():
    root = dump()
    return [nd.get("text") for nd in root.iter("node") if nd.get("text")] if root is not None else []

def top():
    for _ in range(4): scroll(down=False)

def shoot_screen(name, pages=3):
    top(); shot(name)
    for i in range(1, pages):
        scroll(); shot(f"{name}_p{i+1}")
    # record every visible text so nothing is silently missing
    log(f"  - texts on {name}: " + " | ".join(visible_texts()[:60]))

def allow_system_dialog():
    for lab in ["Allow", "ALLOW", "السماح", "Allow all the time", "OK"]:
        b = find(lab)
        if b:
            adb(f"shell input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}"); time.sleep(1.5)
            log(f"- ✅ system dialog: tapped «{lab}»"); return True
    return False

def notif_shade(name):
    adb("shell cmd statusbar expand-notifications"); time.sleep(2); shot(name)

def collapse():
    adb("shell cmd statusbar collapse"); time.sleep(1)

# ------------------------------------------------------------------ run
log("# Omooma UI test report\n")
log("Device: " + adb("shell getprop ro.product.model").strip() + ", Android " + adb("shell getprop ro.build.version.release").strip())
dims()
adb("logcat -c")
adb("install -r app/Omooma.apk")
adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(5)
check("launch")

log("\n## Onboarding")
shoot_screen("onboarding", 2); top()
if tap("اسم الطفل (اختياري)", "baby name field"):
    adb("shell input text Mohammad"); adb("shell input keyevent 111"); time.sleep(1)
tap("3 س", "interval chip 3h")
tap("2.5 س", "interval chip 2.5h")
tap("أوقات ثابتة", "onboarding: fixed times"); tap("كل فترة", "onboarding: interval")
shot("onboarding_filled")
tap("هيا نبدأ", "start button")

log("\n## Permissions")
time.sleep(2); shot("permissions_with_dialog")
allow_system_dialog(); time.sleep(1); shot("permissions_after_notif")
tap("السماح", "battery permission", scrolls=2)
time.sleep(2); shot("battery_dialog"); allow_system_dialog(); time.sleep(2)
adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(2)
shoot_screen("permissions_done", 2)
if not tap("ممتاز، هيا نبدأ", "continue (all granted)", scrolls=3):
    tap("متابعة", "continue")

log("\n## Home + feeding session")
shoot_screen("home_empty", 3); top()
tap("الأيسر", "choose left side")
tap("ابدأ الرضعة", "START feeding")
time.sleep(8); shoot_screen("session_running", 2); top()
notif_shade("session_notification"); collapse()
tap("إيقاف", "PAUSE"); shot("session_paused")
tap("متابعة", "RESUME")
time.sleep(4)
tap("تبديل", "SWITCH SIDE"); time.sleep(4); shot("after_switch")
tap("إنهاء", "FINISH"); shot("finish_sheet")
tap("حسنًا", "close saved sheet")
shoot_screen("home_after_feed", 3)

log("\n## Session from the notification")
top(); tap("ابدأ الرضعة", "start again"); time.sleep(5)
notif_shade("notif_before_actions")
tap("تبديل الجهة", "notification: switch side", scrolls=0); time.sleep(3)
check("after notification switch")
notif_shade("notif_after_switch")
tap("إنهاء", "notification: finish", scrolls=0); time.sleep(2); collapse()
adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(2)
shot("home_after_notification_finish")

log("\n## Quick add")
top(); tap("زجاجة", "quick: bottle"); shot("bottle_sheet")
tap("120", "bottle 120 ml"); tap("زيادة", "bottle +10"); shot("bottle_sheet_set")
tap("حفظ", "save bottle")
tap("حفاض", "quick: diaper"); shot("diaper_sheet"); tap("مبلل ومتّسخ", "diaper type")
tap("تسجيل يدوي", "quick: manual"); shot("manual_sheet")
tap("زيادة", "manual +1 min"); tap("الأيمن", "manual right side")
tap("حفظ", "save manual")
shoot_screen("home_after_quick_add", 3)

log("\n## History")
tap("السجل", "tab: history"); shoot_screen("history", 2); top()
tap("رضاعة بالزجاجة", "open bottle entry"); shot("edit_sheet")
tap("إلغاء", "cancel edit")
tap("إضافة تسجيل", "history + button"); shot("add_sheet"); tap("حفاض", "add sheet: diaper type", scrolls=0); shot("add_sheet_diaper"); tap("حفظ", "save diaper entry")

log("\n## Statistics")
tap("الإحصائيات", "tab: stats"); shoot_screen("stats_7", 3); top()
tap("14 يومًا", "stats 14 days"); shot("stats_14")
tap("30 يومًا", "stats 30 days"); shot("stats_30")
tap("7 أيام", "stats 7 days")

log("\n## Settings")
tap("الإعدادات", "tab: settings"); shoot_screen("settings", 5); top()
tap("اسم الطفل", "edit baby name"); shot("name_sheet"); tap("إلغاء", "cancel name")
tap("أوقات ثابتة", "alarm: fixed times"); shot("fixed_times")
tap("إضافة وقت", "add fixed time"); time.sleep(1); shot("time_picker"); allow_system_dialog()
shot("fixed_time_added")
tap("كل فترة", "alarm: interval")
tap("زيادة", "interval +15")
tap("منبّه قوي", "loud tone"); tap("ناعمة", "soft tone")
tap("تجربة التنبيه الآن", "TEST ALARM"); notif_shade("test_alarm_notification"); collapse()
# toggles
root = dump()
sw = [bounds(nd) for nd in root.iter("node") if nd.get("class") == "android.widget.Switch"] if root is not None else []
log(f"- switches visible: {len(sw)}")
for i in range(6):
    root = dump()
    sws = [bounds(nd) for nd in root.iter("node") if nd.get("class") == "android.widget.Switch" and nd.get("checked") == "false"] if root is not None else []
    sws = [b for b in sws if b and b[1] > 80 and b[3] < H - 250]
    if not sws: scroll(); continue
    b = sws[0]; adb(f"shell input tap {(b[0]+b[2])//2} {(b[1]+b[3])//2}"); time.sleep(1.5)
    if check(f"toggle switch {i+1}"): log(f"- ✅ toggle switch {i+1} on")
shoot_screen("settings_all_on", 5); top()
tap("ليلي", "night mode", scrolls=6); shoot_screen("settings_night", 3)
tap("الرئيسية", "tab: home (night)"); shoot_screen("home_night", 3)
tap("الإعدادات", "back to settings"); tap("نهاري", "day mode", scrolls=6)
tap("ضبط الأذونات", "open permissions", scrolls=6); shot("permissions_from_settings")
tap("ممتاز، هيا نبدأ", "close permissions", scrolls=3) or tap("متابعة", "close permissions")

log("\n## Alarms scheduled (dumpsys)")
al = adb("shell dumpsys alarm")
mine = [l.strip() for l in al.splitlines() if PKG in l][:20]
log("```\n" + "\n".join(mine) + "\n```")
open(OUT + "/dumpsys_alarm.txt", "w").write(al)

log("\n## Large font (1.3)")
adb("shell settings put system font_scale 1.3")
adb(f"shell am force-stop {PKG}"); adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(4)
check("relaunch large font")
shoot_screen("big_home", 3)
tap("الأيسر", "big: choose side"); tap("ابدأ الرضعة", "big: start"); time.sleep(3); shot("big_session")
tap("إنهاء", "big: finish"); tap("حسنًا", "big: close sheet")
tap("السجل", "big: history"); shoot_screen("big_history", 1)
tap("الإحصائيات", "big: stats"); shoot_screen("big_stats", 3)
tap("الإعدادات", "big: settings"); shoot_screen("big_settings", 5)
adb("shell settings put system font_scale 1.0")

log("\n## Reboot survival")
adb("reboot"); sh("adb wait-for-device"); 
for _ in range(60):
    if adb("shell getprop sys.boot_completed").strip() == "1": break
    time.sleep(3)
time.sleep(10)
al2 = adb("shell dumpsys alarm")
log("- alarms after reboot: " + str(sum(1 for l in al2.splitlines() if PKG in l)) + " lines mention the app")
adb(f"shell am start -n {PKG}/.MainActivity"); time.sleep(4); check("launch after reboot"); shot("after_reboot")

crash = adb("logcat -d -b crash")
open(OUT + "/logcat_crash.txt", "w").write(crash)
errs = sh(f"adb logcat -d | grep -E 'FATAL|AndroidRuntime|{PKG}' | tail -200")
open(OUT + "/logcat_app.txt", "w").write(errs)
log("\n## Summary")
log(f"- screenshots: {n[0]}")
log(f"- problems: {len(fails)}" + ("" if not fails else " → " + ", ".join(fails)))
log("- crash buffer: " + ("EMPTY (no crashes)" if not crash.strip() else "HAS CRASHES, see logcat_crash.txt"))
