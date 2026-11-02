#!/usr/bin/env python3
"""Inventory production exception and API failure boundaries without changing code."""
import json, re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
workspace = root.parent
catalog = {entry['name']: entry for entry in json.loads((root/'contracts/errors.json').read_text())}
rows = []
paths = sorted(root.rglob('*.java'))
frontend = workspace/'lab-system-frontend/src'
if frontend.exists():
    paths += sorted(frontend.rglob('*.ts')) + sorted(frontend.rglob('*.tsx'))
for path in paths:
    backend = path.suffix == '.java'
    if backend and '/src/main/' not in str(path): continue
    if not backend and ('.test.' in path.name or path.name == 'errorCodes.ts'): continue
    lines = path.read_text().splitlines()
    for index, line in enumerate(lines):
        if not re.search(r'throw new \w*(?:Exception|Error)|R\.(?:fail|badRequest|forbidden|unauthorized|serverError|notFound)|catch\s*\(', line): continue
        expression = ' '.join(lines[index:index+3]).strip()
        name = None
        match = re.search(r'new BusinessException\(ErrorCode\.(\w+)', expression)
        if match: name = match[1]
        elif 'AuthenticationRequiredException' in line: name = 'AUTHENTICATION_REQUIRED'
        elif 'PermissionDeniedException' in line: name = 'PERMISSION_DENIED'
        elif 'AuthorizationConfigurationException' in line: name = 'AUTH_CONFIGURATION_ERROR'
        elif 'throw new IllegalArgumentException' in line: name = 'VALIDATION_FAILED'
        elif re.search(r'throw new (?!BusinessException)\w*Exception', line): name = 'INTERNAL_ERROR'
        else:
            for method, symbol in {'badRequest':'VALIDATION_FAILED','unauthorized':'AUTHENTICATION_REQUIRED','forbidden':'PERMISSION_DENIED','notFound':'RESOURCE_NOT_FOUND','serverError':'INTERNAL_ERROR'}.items():
                if f'R.{method}(' in line: name = symbol
        entry = catalog.get(name)
        local_validation = not backend and 'throw new Error' in line
        rows.append({
            'source': str(path.relative_to(workspace)), 'line': index+1,
            'expression': line.strip(),
            'conditionContext': '\n'.join(lines[max(0,index-2):index+1]).strip(),
            'classification': 'local validation/preview invariant' if local_validation else 'typed business' if match else 'boundary validation' if name == 'VALIDATION_FAILED' else 'internal/background or common boundary' if backend else 'frontend lifecycle/local error handling',
            'businessCode': entry['code'] if entry else None,
            'httpStatus': entry['httpStatus'] if entry else None,
            'defaultMessage': entry['message'] if entry else None,
            'codeSource': 'catalog' if entry else 'ApiError.code (for API errors); local Error has no API code' if not backend else 'propagated RpcError/BusinessException or normalized by RpcErrors/Advice; background-only failures are logged',
            'presentation': 'local' if local_validation else 'global by default; explicit local/silent override' if not backend else 'API failures use shared frontend policy; background-only failures are logged',
        })
(root/'contracts/exception-scan.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
print(f'Inventoried {len(rows)} production error/handling sites')
