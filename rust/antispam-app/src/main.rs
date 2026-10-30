mod screening;

use slint::{ModelRc, SharedString, VecModel};
use std::cell::RefCell;
use std::rc::Rc;

slint::include_modules!();

fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn main() -> Result<(), slint::PlatformError> {
    antispam_core::init();
    let app = AppWindow::new()?;

    let modes: Vec<SharedString> = screening::MODE_LABELS.iter().map(|s| SharedString::from(*s)).collect();
    app.set_modes(ModelRc::new(VecModel::from(modes)));
    app.set_mode_index(2);

    let history: Rc<VecModel<HistoryItem>> = Rc::new(VecModel::default());
    app.set_history(ModelRc::from(history.clone()));

    let mode_index = Rc::new(RefCell::new(2_i32));

    {
        let mode_index = mode_index.clone();
        app.on_mode_changed(move |index| {
            *mode_index.borrow_mut() = index;
        });
    }

    {
        let weak = app.as_weak();
        let history = history.clone();
        let mode_index = mode_index.clone();
        app.on_verify(move |raw| {
            let Some(app) = weak.upgrade() else { return };
            if raw.trim().is_empty() {
                app.set_verdict_action(SharedString::from(""));
                app.set_verdict_text(SharedString::from("Digite um número para verificar."));
                return;
            }
            let verdict = screening::verify(raw.as_str(), *mode_index.borrow(), now_unix());
            app.set_verdict_action(SharedString::from(verdict.action));
            app.set_verdict_text(SharedString::from(verdict.explanation.as_str()));
            history.insert(
                0,
                HistoryItem {
                    number: SharedString::from(verdict.number.as_str()),
                    action: SharedString::from(verdict.action),
                    explanation: SharedString::from(verdict.explanation.as_str()),
                },
            );
        });
    }

    app.run()
}
