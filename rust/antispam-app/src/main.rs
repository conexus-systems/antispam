slint::include_modules!();

fn main() -> Result<(), slint::PlatformError> {
    antispam_core::init();
    let app = AppWindow::new()?;
    app.run()
}
