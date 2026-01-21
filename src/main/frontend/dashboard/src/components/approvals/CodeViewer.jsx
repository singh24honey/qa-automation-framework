import React, { useEffect } from 'react';
import Prism from 'prismjs';
import 'prismjs/themes/prism-tomorrow.css';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-python';
import './CodeViewer.css';

/**
 * Code Viewer with Syntax Highlighting
 */
const CodeViewer = ({ code, language = 'java', title }) => {
    useEffect(() => {
        Prism.highlightAll();
    }, [code]);

    return (
        <div className="code-viewer">
            {title && <div className="code-viewer-title">{title}</div>}
            <pre className="code-viewer-content">
                <code className={`language-${language.toLowerCase()}`}>
                    {code}
                </code>
            </pre>
        </div>
    );
};

export default CodeViewer;