#include "stdafx.h"
#include "AeroNode.h"
#include <assert.h>
#include <algorithm>
#include <list>

#include <Shlobj.h>

#include "logger.h"
#include "string_helpers.h"

AeroNode::AeroNode(const std::wstring& name, AeroNode* parent)
	: m_name(lowercase(name)),
	m_parent(parent),
	m_children(),
	m_ownStatus(NoStatus),
	m_childrenStatus(NoStatus)
{
	assert(!m_name.empty());
}

AeroNode::~AeroNode()
{
	// Delete all children nodes
	for (auto it = m_children.begin(); it != m_children.end(); ++it) {
		delete it->second;
	}
}


/**
 * Returns a pointer to a AeroNode at the given path.
 * If createPath is true, creates the intermediary nodes if they don't exist (like 'mkdir -p')
 * Note: if createPath is true, this method could still return NULL if the path doesn't match the root node
*/
AeroNode* AeroNode::nodeAtPath(const std::wstring& p, bool createPath)
{
	std::wstring root = m_name;;
	std::wstring path = lowercase(p);

	if (!str_starts_with(path, root)) {
		return NULL; // path is not under root
	}

	if (path.length() == root.length()) {
		return this; // path and root are the same
	}

	if (path.at(root.length()) != L'\\') {
		return NULL; // ensure path is indeed under root (there's a path separator)
	}

	// Remove root from the path and get a vector with the other path components
	path = path.substr(root.length() + 1); // +1 to remove the path separator
	std::vector<std::wstring> pathComponents = split(path, L'\\');

	if (pathComponents.empty()) {
		// edge case where path equals root plus one or more trailing path separators
		return this;
	}

	return nodeAtPath(pathComponents, 0, createPath);
}

/**
 * Internal helper function that is called recursively to find the node for a given path
 * @param: pathComponents: a vector with path components
 * index: current position in the path component vector. We increase this variable as we recurse.
 * if true, create intermediate nodes in the path if they don't exist
 */
AeroNode* AeroNode::nodeAtPath(const std::vector<std::wstring>& pathComponents, int index, bool create)
{
	if (index == pathComponents.size()) {
		assert(m_name == pathComponents[index - 1]);
		return this;
	}

	const std::wstring& component = pathComponents[index];
	AeroNode* child;
	auto it = m_children.find(component);
	if (it == m_children.end()) {
		if (!create) {
			return NULL;
		}
		child = new AeroNode(component, this);
		m_children[component] = child;
	} else {
		child = it->second;
	}

	index++;
	return child->nodeAtPath(pathComponents, index, create);
}

AeroNode::Status AeroNode::status() const
{
	return m_ownStatus | m_childrenStatus;
}

void AeroNode::setStatus(Status status)
{
	m_ownStatus = status;

	std::wstring path = pathOfNode();
	DEBUG_LOG("Notifying the shell of overlay change: " << path);
	SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATH | SHCNF_FLUSHNOWAIT, path.c_str(), NULL);

	if (m_parent) {
		m_parent->updateChildrenStatus();
	}
}

/**
Update the node's childrenStatus field with the status of all children
If the status has changed, recursively call the parent's updateChildrenStatus
*/
void AeroNode::updateChildrenStatus()
{
	Status oldStatus = status();

	m_childrenStatus = NoStatus;

	auto iter = m_children.begin();
	while (iter != m_children.end()) {
		AeroNode* child = iter->second;
		Status st = child->status();
		m_childrenStatus |= st;
		if (st == NoStatus) {
			m_children.erase(iter++);
			delete child;
		} else {
			++iter;
		}
	}

	if (status() != oldStatus) {
		std::wstring path = pathOfNode();
		DEBUG_LOG("Notifying the shell of overlay change: " << path);
		SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATH | SHCNF_FLUSHNOWAIT, path.c_str(), NULL);

		if (m_parent) {
			m_parent->updateChildrenStatus();
		}
	}
}

std::wstring AeroNode::name() const
{
	return m_name;
}

AeroNode* AeroNode::parent() const
{
	return m_parent;
}

std::map<std::wstring, AeroNode*> AeroNode::children()
{
	return m_children;
}

/**
 * Return the full path of this node
 */
std::wstring AeroNode::pathOfNode() const
{
	// Create a list of all parents
	const AeroNode*  node = this;
	std::list<const AeroNode*> parents;
	do {
		parents.push_front(node);
	} while (node = node->parent());

	// Write the name of all parents in a string
	std::wstring path;
	path.reserve(MAX_PATH);
	for(auto iter = parents.begin(); iter != parents.end(); ++iter) {
		path += (*iter)->name() + L'\\';
	}

	// drop the trailing slash
	path.resize(path.length() - 1);

	return path;
}
