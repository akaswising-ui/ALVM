use std::env;
use std::process::ExitCode;

fn usage() {
    eprintln!("CI Runtime (Rust)\n\nUsage:\n  ci-runtime self-test\n  ci-runtime run <file.room> [entry] [event]");
}

fn main() -> ExitCode {
    let args: Vec<String> = env::args().collect();
    if args.len() == 2 && args[1] == "self-test" {
        if ci_runtime::self_test() {
            println!("CI Runtime {}: self-test passed", ci_runtime::BUNDLED_VERSION);
            return ExitCode::SUCCESS;
        }
        eprintln!("CI Runtime self-test failed");
        return ExitCode::FAILURE;
    }

    if args.len() >= 3 && args[1] == "run" {
        let entry = args.get(3).map(String::as_str).unwrap_or("main");
        let event = args.get(4).map(String::as_str).unwrap_or("");
        let result = ci_runtime::run_room(&args[2], entry, event);
        if result["ok"].as_bool() == Some(true) {
            let output = result["output"].as_str().unwrap_or("");
            if !output.is_empty() {
                println!("{output}");
            }
            if let Some(ui) = result.get("ui") {
                println!("{}", serde_json::to_string(ui).unwrap_or_default());
            }
            ExitCode::SUCCESS
        } else {
            eprintln!("{}", result["error"].as_str().unwrap_or("CI Runtime failed"));
            ExitCode::FAILURE
        }
    } else {
        usage();
        ExitCode::from(2)
    }
}
