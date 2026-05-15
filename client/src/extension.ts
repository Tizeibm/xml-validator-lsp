import * as vscode from 'vscode';
import * as path from 'path';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind,
} from 'vscode-languageclient/node';

// ─────────────────────────────────────────────────────────────────────────────
// Types for custom LSP protocol messages
// ─────────────────────────────────────────────────────────────────────────────

/** A single validation error received from the server. */
interface ValidationError {
    line: number;   // 0-based (LSP convention)
    column: number;
    severity: string; // WARNING, ERROR, FATAL
    message: string;
}

/** Payload of the custom/validationBatch notification. */
interface ValidationBatchParams {
    errors: ValidationError[];
}

/** Payload of the custom/validationFinished notification. */
interface ValidationFinishedParams {
    elapsedTimeMs: number;
    totalErrors: number;
    xmlUri: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Module-level state
// ─────────────────────────────────────────────────────────────────────────────

let client: LanguageClient | undefined;
let outputChannel: vscode.OutputChannel;

// ─────────────────────────────────────────────────────────────────────────────
// Extension Lifecycle
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Called when the extension is activated.
 * Sets up the OutputChannel, registers the validation command,
 * and starts the Java LSP server.
 */
export async function activate(context: vscode.ExtensionContext): Promise<void> {
    // Create a dedicated output channel for validation results
    outputChannel = vscode.window.createOutputChannel('Mass XML Validator');

    // Register the main command
    const command = vscode.commands.registerCommand(
        'xmlValidator.validateHugeFile',
        () => handleValidateCommand(context)
    );
    context.subscriptions.push(command);

    // Start the LSP client/server
    await startLanguageClient(context);
}

/**
 * Called when the extension is deactivated. Stops the LSP client.
 */
export async function deactivate(): Promise<void> {
    if (client) {
        await client.stop();
        client = undefined;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LSP Client Setup
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Starts the Java LSP server as a child process and connects
 * the LanguageClient to it via stdin/stdout JSON-RPC.
 */
async function startLanguageClient(context: vscode.ExtensionContext): Promise<void> {
    // Resolve the path to the server JAR (bundled with the extension)
    const serverJarPath = path.join(
        context.extensionPath,
        'server',
        'xml-validator-server.jar'
    );

    // Read user configuration
    const config = vscode.workspace.getConfiguration('xmlValidator');
    const javaPath = config.get<string>('javaPath', 'java');
    const jvmArgs = config.get<string[]>('jvmArgs', ['-Xmx512m', '-Xms128m']);

    // Build the Java command to launch the server
    const serverOptions: ServerOptions = {
        command: javaPath,
        args: [...jvmArgs, '-jar', serverJarPath],
        transport: TransportKind.stdio,
    };

    // Client options — minimal, as we don't use standard document sync
    const clientOptions: LanguageClientOptions = {
        // We register for XML files but never actually sync their content.
        // This is required by the LanguageClient API but has no practical effect
        // since our server sets TextDocumentSyncKind.None.
        documentSelector: [{ scheme: 'file', language: 'xml' }],
    };

    // Create and start the client
    client = new LanguageClient(
        'xmlValidatorLsp',
        'Mass XML Validator',
        serverOptions,
        clientOptions
    );

    // Register handlers for custom notifications from the server
    client.onNotification('custom/validationBatch', (params: ValidationBatchParams) => {
        handleValidationBatch(params);
    });

    client.onNotification('custom/validationFinished', (params: ValidationFinishedParams) => {
        handleValidationFinished(params);
    });

    // Start the client (this launches the Java process)
    await client.start();
    outputChannel.appendLine('[INFO] LSP server started successfully.');
}

// ─────────────────────────────────────────────────────────────────────────────
// Command Handler
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Handles the "Valider un gros fichier XML" command.
 *
 * Opens two file dialogs (XML, then XSD), sends a custom/startValidation
 * notification to the server, and shows a progress spinner.
 */
async function handleValidateCommand(context: vscode.ExtensionContext): Promise<void> {
    // ── Step 1: Select the XML file ──────────────────────────────────────
    const xmlFiles = await vscode.window.showOpenDialog({
        canSelectMany: false,
        openLabel: 'Sélectionner le fichier XML',
        filters: {
            'XML Files': ['xml'],
            'All Files': ['*'],
        },
        title: 'Sélectionner le fichier XML à valider',
    });

    if (!xmlFiles || xmlFiles.length === 0) {
        return; // User cancelled
    }

    // ── Step 2: Select the XSD schema ────────────────────────────────────
    const xsdFiles = await vscode.window.showOpenDialog({
        canSelectMany: false,
        openLabel: 'Sélectionner le fichier XSD',
        filters: {
            'XSD Schema Files': ['xsd'],
            'All Files': ['*'],
        },
        title: 'Sélectionner le schéma XSD de validation',
    });

    if (!xsdFiles || xsdFiles.length === 0) {
        return; // User cancelled
    }

    const xmlUri = xmlFiles[0].toString();
    const xsdUri = xsdFiles[0].toString();

    // ── Step 3: Prepare the OutputChannel ────────────────────────────────
    outputChannel.clear();
    outputChannel.show(true); // Show the output panel but don't steal focus
    outputChannel.appendLine('══════════════════════════════════════════════════════════');
    outputChannel.appendLine('  MASS XML VALIDATOR — Validation en cours...');
    outputChannel.appendLine('══════════════════════════════════════════════════════════');
    outputChannel.appendLine(`  Fichier XML : ${xmlFiles[0].fsPath}`);
    outputChannel.appendLine(`  Schéma XSD  : ${xsdFiles[0].fsPath}`);
    outputChannel.appendLine('──────────────────────────────────────────────────────────');
    outputChannel.appendLine('');

    // ── Step 4: Check the LSP client is ready ────────────────────────────
    if (!client) {
        vscode.window.showErrorMessage(
            'Le serveur LSP n\'est pas encore démarré. Veuillez réessayer dans quelques secondes.'
        );
        return;
    }

    // ── Step 5: Show progress spinner & send custom notification ─────────
    vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: 'Validation XML en cours...',
            cancellable: false,
        },
        (progress) => {
            // This promise resolves when the server sends custom/validationFinished.
            // We store the resolver so handleValidationFinished can call it.
            return new Promise<void>((resolve) => {
                progressResolver = resolve;
            });
        }
    );

    // ── Step 6: Send the custom/startValidation notification ─────────────
    client.sendNotification('custom/startValidation', {
        xmlUri: xmlUri,
        xsdUri: xsdUri,
    });

    outputChannel.appendLine('[INFO] Notification custom/startValidation envoyée au serveur.');
    outputChannel.appendLine('');
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Notification Handlers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolver function for the progress spinner promise.
 * Set when the progress dialog is shown, called when validation finishes.
 */
let progressResolver: (() => void) | undefined;

/**
 * Handles a batch of validation errors received from the server.
 * Writes each error as plain text to the OutputChannel.
 *
 * Format: [Ligne X, Col Y] SEVERITY: message
 *
 * No clickable links are generated to prevent accidental file opening
 * (which would crash VS Code for 100 GB+ files).
 */
function handleValidationBatch(params: ValidationBatchParams): void {
    if (!params.errors || params.errors.length === 0) {
        return;
    }

    for (const error of params.errors) {
        // Convert back to 1-based line for human-readable display
        const displayLine = error.line + 1;
        outputChannel.appendLine(
            `[Ligne ${displayLine}, Col ${error.column}] ${error.severity}: ${error.message}`
        );
    }
}

/**
 * Handles the validation finished signal from the server.
 * Displays final metrics and closes the progress spinner.
 */
function handleValidationFinished(params: ValidationFinishedParams): void {
    const elapsedSec = (params.elapsedTimeMs / 1000).toFixed(2);

    outputChannel.appendLine('');
    outputChannel.appendLine('══════════════════════════════════════════════════════════');
    outputChannel.appendLine('  VALIDATION TERMINÉE');
    outputChannel.appendLine('══════════════════════════════════════════════════════════');
    outputChannel.appendLine(`  Temps écoulé    : ${elapsedSec} secondes`);
    outputChannel.appendLine(`  Erreurs totales : ${params.totalErrors}`);
    outputChannel.appendLine('══════════════════════════════════════════════════════════');

    // Show a toast notification
    if (params.totalErrors === 0) {
        vscode.window.showInformationMessage(
            `✅ Validation terminée — Aucune erreur trouvée (${elapsedSec}s)`
        );
    } else {
        vscode.window.showWarningMessage(
            `⚠️ Validation terminée — ${params.totalErrors} erreur(s) trouvée(s) (${elapsedSec}s). Voir le panneau "Mass XML Validator".`
        );
    }

    // Resolve the progress spinner promise to close the spinner
    if (progressResolver) {
        progressResolver();
        progressResolver = undefined;
    }
}
